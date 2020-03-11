import java.text.SimpleDateFormat
import java.util.TimeZone

val df = {
  val _df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
  _df.setTimeZone(TimeZone.getTimeZone("UTC"))
  _df
}

val now = System.currentTimeMillis()

df.format(now)

df.format(now + 31557600000L)
