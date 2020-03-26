package com.ubirch.protocol.codec;


import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.*;
import com.ubirch.protocol.ProtocolException;
import com.ubirch.protocol.ProtocolMessage;
import org.msgpack.core.*;
import org.msgpack.jackson.dataformat.MessagePackFactory;
import org.msgpack.value.ImmutableStringValue;
import org.msgpack.value.ValueFactory;
import org.msgpack.value.ValueType;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.*;


public class KeyMsgPackProtocolDecoder extends ProtocolDecoder<byte[]> {
    private final static com.ubirch.protocol.codec.KeyMsgPackProtocolDecoder instance = new com.ubirch.protocol.codec.KeyMsgPackProtocolDecoder();

    public static com.ubirch.protocol.codec.KeyMsgPackProtocolDecoder getDecoder() {
        return instance;
    }

    private ObjectMapper mapper;

    @SuppressWarnings("WeakerAccess")
    KeyMsgPackProtocolDecoder() {
        mapper = new ObjectMapper(new MessagePackFactory());
        mapper.configure(DeserializationFeature.USE_BIG_INTEGER_FOR_INTS, true);
    }

    /**
     * Decode a a protocol message from it's raw data.
     *
     * @param message the raw protocol message in msgpack format
     * @return the decoded protocol message
     * @throws ProtocolException if the decoding failed
     */
    @SuppressWarnings("checkstyle:FallThrough")
    @Override
    public ProtocolMessage decode(byte[] message) throws ProtocolException {
        ByteArrayInputStream in = new ByteArrayInputStream(message);

        MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(in);
        ProtocolMessage pm = new ProtocolMessage();
        try {
            ValueType envelopeType = unpacker.getNextFormat().getValueType();
            int envelopeLength = unpacker.unpackArrayHeader();
            if (envelopeLength > 4 && envelopeLength < 7) {
                pm.setVersion(unpacker.unpackInt());

                pm.setUUID(UUIDUtil.bytesToUUID(unpacker.readPayload(unpacker.unpackRawStringHeader())));
                switch (pm.getVersion() & 0x0F) {
                    case ProtocolMessage.CHAINED & 0x0F:
                        pm.setChain(unpacker.readPayload(unpacker.unpackRawStringHeader()));
                        break;
                    case ProtocolMessage.SIGNED & 0x0F:
                        break;
                    default:
                        throw new ProtocolException(String.format("unknown protocol type: 0x%04x", pm.getVersion() & 0x0F));
                }
                pm.setHint(unpacker.unpackInt());
                pm.setPayload(decodePayload(unpacker));

                // finally store the signed data and signature for later verification
                pm.setSigned(Arrays.copyOfRange(message, 0, (int) unpacker.getTotalReadBytes()));
                pm.setSignature(unpacker.readPayload(unpacker.unpackRawStringHeader()));

                return pm;
            } else {
                throw new ProtocolException(String.format("unknown msgpack envelope format: %s[%d]", envelopeType.name(), envelopeLength));
            }
        } catch (MessagePackException e) {
            throw new ProtocolException("msgpack decoding failed", e);
        } catch (IOException e) {
            throw new ProtocolException(String.format("msgpack data corrupt at position %d", unpacker.getTotalReadBytes()), e);
        }
    }

    private JsonNode decodePayload(MessageUnpacker unpacker) throws IOException {
        MessageFormat mf = unpacker.getNextFormat();
        switch (mf.getValueType()) {
            case NIL:
                unpacker.unpackNil();
                return NullNode.getInstance();
            case BOOLEAN:
                return BooleanNode.valueOf(unpacker.unpackBoolean());
            case INTEGER:
                if (mf == MessageFormat.UINT64) {
                    return BigIntegerNode.valueOf(unpacker.unpackBigInteger());
                }
                return LongNode.valueOf(unpacker.unpackLong());
            case FLOAT:
                return DoubleNode.valueOf(unpacker.unpackDouble());
            case STRING: {
                int length = unpacker.unpackRawStringHeader();
                ImmutableStringValue stringValue = ValueFactory.newString(unpacker.readPayload(length), true);
                if (stringValue.isRawValue()) {
                    return BinaryNode.valueOf(stringValue.asRawValue().asByteArray());
                } else {
                    return TextNode.valueOf(stringValue.asString());
                }
            }
            case BINARY: {
                int length = unpacker.unpackBinaryHeader();
                return BinaryNode.valueOf(unpacker.readPayload(length));
            }
            case ARRAY: {
                int size = unpacker.unpackArrayHeader();
                List<JsonNode> array = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    array.add(decodePayload(unpacker));
                }
                return new ArrayNode(null, array);
            }
            case MAP: {
                int size = unpacker.unpackMapHeader();
                Map<String, JsonNode> kvs = new HashMap<>(size);
                for (int i = 0; i < size; i++) {
                    JsonNode kn = decodePayload(unpacker);
                    String key = kn.isBinary() ? new String(kn.binaryValue()) : kn.asText();
                    kvs.put(key, decodePayload(unpacker));
                }
                return new ObjectNode(null, kvs);
            }
            case EXTENSION: {
                ExtensionTypeHeader extHeader = unpacker.unpackExtensionTypeHeader();
                return BinaryNode.valueOf(unpacker.readPayload(extHeader.getLength()));
            }
            default:
                throw new MessageNeverUsedFormatException("Unknown value type");
        }
    }
}
