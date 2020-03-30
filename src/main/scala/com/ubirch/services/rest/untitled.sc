import java.net.URLDecoder
import java.nio.charset.{Charset, StandardCharsets}

val res = """%e8%b1X:yY%11%baH!%f6%d1YW%fb%b2%a8%b6X%9f%ac%0e(%f0F%9f%1d]FN%179"""


"""\e8\b1X:yY\11\baH!\f6\d1YW\fb\b2\a8\b6X\9f\ac\0e(\f0F\9f\1d]FN\179""".replaceAll("""\\""".stripMargin, "%")

URLDecoder.decode(res, "ascii")
URLDecoder.decode(res)
URLDecoder.decode(res, "utf-8")
URLDecoder.decode(res, StandardCharsets.ISO_8859_1.name())
URLDecoder.decode(res, StandardCharsets.ISO_8859_1.name()).getBytes(StandardCharsets.ISO_8859_1)
URLDecoder.decode(res, StandardCharsets.ISO_8859_1.name()).getBytes()

println(res.getBytes(StandardCharsets.US_ASCII))
res.getBytes(StandardCharsets.US_ASCII)
println(res.getBytes(StandardCharsets.ISO_8859_1))
res.getBytes(StandardCharsets.ISO_8859_1)
println(res.getBytes(StandardCharsets.UTF_8))
res.getBytes(StandardCharsets.UTF_8)
println(res.getBytes(StandardCharsets.UTF_16))
res.getBytes(StandardCharsets.UTF_16)

"""0xe80xb10x58:yY%11%baH!%f6%d1YW%fb%b2%a8%b6X%9f%ac%0e(%f0F%9f%1d]FN%179"""
