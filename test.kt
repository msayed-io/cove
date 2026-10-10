import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.YoutubeDL

fun main() {
    val req = YoutubeDLRequest("https://www.youtube.com/watch?v=aqz-KE-bpKQ")
    val info = YoutubeDL.getInstance().getInfo(req)
    println(info.url)
}
