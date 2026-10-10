package com.example

import java.util.Locale

object ContentFilter {
    private val BLOCKED_KEYWORDS_AR = listOf(
        "اغنية", "أغنية", "اغاني", "أغاني", "اغنيه", "أغنيه", "اغنبه", "اغنبية", 
        "موسيقى", "موسيقه", "موسيقا", "موسيقي", "ميوزك", "ميوزيك", "ميوزيق",
        "ريمكس", "ريمكسات", "طرب", "طربيات", "مطرب", "مطربة", "مغني", "مغنية", "غناء", "غنا",
        "البوم", "ألبوم", "البومات", "ألبومات", "كليب", "فيديو كليب", "شيلات", "شيله", "شيلة",
        "معزوفة", "معزوفه", "عزف", "معزوفات", "دي جي", "ديجي", "دجي", "راب", "رابر",
        "دندنه", "دندنة", "دندنها", "طبل", "عود", "قيتار", "جيتار", "بيانو", "الحان", "ألحان",
        "لحن", "نغمه", "نغمة", "رنات", "رنه", "رنة", "نغمات", "مهرجان", "مهرجانات"
    )

    private val BLOCKED_KEYWORDS_EN = listOf(
        "song", "songs", "music", "remix", "remixes", "track", "tracks", "album", "albums",
        "lyrics", "lyric", "mp3", "karaoke", "instrumental", "instrumentals", "playlist", "playlists",
        "dj", "djs", "pop", "rap", "hiphop", "jazz", "acoustic", "chord", "chords", "guitar", "piano",
        "synth", "beat", "beats", "singer", "singers", "concert", "concerts", "videoclip", "video clip",
        "soundcloud", "spotify", "anghami", "deezer", "tidal", "apple music", "billboard", "shazam"
    )

    private val BLOCKED_SINGERS_AR = listOf(
        "عمرو دياب", "تامر حسني", "شيرين عبد الوهاب", "شيرين عبدالوهاب", "نانسي عجرم", "اليسا", "إليسا", 
        "اصاله", "أصالة", "حماقي", "محمد حماقي", "محمد رمضان", "فضل شاكر", "كاظم الساهر", "راغب علامه", 
        "راغب علامة", "جورج وسوف", "هيفاء وهبي", "ميريام فارس", "حسين الجسمي", "ماجد المهندس", "راشد الماجد", 
        "عبد المجيد عبد الله", "عبدالمجيد عبدالله", "رابح صقر", "محمد عبده", "ام كلثوم", "أم كلثوم", 
        "عبد الحليم حافظ", "عبدالحليم حافظ", "فريد الاطرش", "فريد الأطرش", "فيروز", "محمد منير", 
        "تامر عاشور", "بهاء سلطان", "احمد سعد", "أحمد سعد", "ويجز", "مروان بابلو", "بابلو", "شاكوش", 
        "حسن شاكوش", "عمر كمال", "حمو بيكا", "محمود العسيلي", "العسيلي", "عمرو مصطفى", "كارول سماحة", 
        "كارول سماحه", "يارا", "مروان خوري", "صابر الرباعي", "ملحم زين", "سعد لمجرد", "زهير البهاوي", 
        "حاتم عمور", "ناصيف زيتون", "زياد برجي", "جوزيف عطية", "جوزيف عطيه", "رامي عياش", "رامي جمال", 
        "حمادة هلال", "حماده هلال", "مصطفى حجاج", "مصطفي حجاج", "روبي"
    )

    private val BLOCKED_SINGERS_EN = listOf(
        "taylor swift", "ariana grande", "justin bieber", "eminem", "drake", "the weeknd", "shakira", 
        "rihanna", "selena gomez", "billie eilish", "bruno mars", "ed sheeran", "coldplay", "adele", 
        "beyonce", "dua lipa", "katy perry", "lady gaga", "kanye west", "travis scott", "post malone", 
        "shawn mendes", "camila cabello", "cardi b", "nicki minaj", "bts", "blackpink", "olivia rodrigo", 
        "harry styles", "zayn malik", "one direction", "snoop dogg", "lil nas x", "doja cat", "maroon 5", 
        "demi lovato", "pitbull"
    )

    private val BLOCKED_DOMAINS = listOf(
        "spotify.com", "soundcloud.com", "anghami.com", "deezer.com", "tidal.com",
        "music.apple.com", "music.youtube.com", "smule.com", "shazam.com", "genius.com",
        "pandora.com", "bandcamp.com", "soundclick.com", "mp3juice", "mp3juices", "dndnha", "mawaly",
        "sm3na", "sm3ha", "nogomi", "melody4arab"
    )

    /**
     * Checks if a given text contains any blocked keywords or singer names (normalized check)
     */
    fun containsBlockedContent(text: String?): Boolean {
        if (text == null || text.isBlank()) return false
        
        val normalized = text.lowercase(Locale.getDefault())
            .replace("أ", "ا")
            .replace("إ", "ا")
            .replace("آ", "ا")
            .replace("ة", "ه")
            .replace("ى", "ي")
            .trim()

        // 1. Check Arabic normalized keywords
        for (keyword in BLOCKED_KEYWORDS_AR) {
            val normalizedKeyword = keyword
                .replace("أ", "ا")
                .replace("إ", "ا")
                .replace("آ", "ا")
                .replace("ة", "ه")
                .replace("ى", "ي")
            
            if (normalized.contains(normalizedKeyword)) {
                return true
            }
        }

        // 2. Check English keywords
        for (keyword in BLOCKED_KEYWORDS_EN) {
            if (normalized.contains(keyword)) {
                return true
            }
        }

        // 3. Check Arabic singers
        for (singer in BLOCKED_SINGERS_AR) {
            val normalizedSinger = singer
                .replace("أ", "ا")
                .replace("إ", "ا")
                .replace("آ", "ا")
                .replace("ة", "ه")
                .replace("ى", "ي")
            
            if (normalized.contains(normalizedSinger)) {
                return true
            }
        }

        // 4. Check English singers
        for (singer in BLOCKED_SINGERS_EN) {
            if (normalized.contains(singer)) {
                return true
            }
        }

        return false
    }

    /**
     * Checks if a given URL is a blocked music-related site or contains blocked queries
     */
    fun isBlockedUrl(url: String?): Boolean {
        if (url == null || url.isBlank()) return false
        val normalizedUrl = url.lowercase(Locale.getDefault())
        
        // Check domains
        for (domain in BLOCKED_DOMAINS) {
            if (normalizedUrl.contains(domain)) {
                return true
            }
        }

        // Check if query parameters in URLs (like google search, youtube search, tiktok search) contain blocked keywords
        if (normalizedUrl.contains("q=") || normalizedUrl.contains("query=") || normalizedUrl.contains("search_query=") || normalizedUrl.contains("keyword=")) {
            // Extract the query part to check
            val queryParam = normalizedUrl.substringAfter("q=", "").substringBefore("&")
            val queryParam2 = normalizedUrl.substringAfter("query=", "").substringBefore("&")
            val queryParam3 = normalizedUrl.substringAfter("search_query=", "").substringBefore("&")
            val queryParam4 = normalizedUrl.substringAfter("keyword=", "").substringBefore("&")
            
            val decoded1 = try { java.net.URLDecoder.decode(queryParam, "UTF-8") } catch(e: Exception) { queryParam }
            val decoded2 = try { java.net.URLDecoder.decode(queryParam2, "UTF-8") } catch(e: Exception) { queryParam2 }
            val decoded3 = try { java.net.URLDecoder.decode(queryParam3, "UTF-8") } catch(e: Exception) { queryParam3 }
            val decoded4 = try { java.net.URLDecoder.decode(queryParam4, "UTF-8") } catch(e: Exception) { queryParam4 }

            if (containsBlockedContent(decoded1) || containsBlockedContent(decoded2) || 
                containsBlockedContent(decoded3) || containsBlockedContent(decoded4)) {
                return true
            }
        }

        // Generative check over whole URL path just in case they put it in the path
        if (containsBlockedContent(normalizedUrl)) {
            // Exceptions: let's allow basic domains like google.com or youtube.com themselves unless the query triggers a block
            if (normalizedUrl.contains("youtube.com") || normalizedUrl.contains("google.com") || 
                normalizedUrl.contains("facebook.com") || normalizedUrl.contains("tiktok.com")) {
                return false // the query parameter check above handles their search block
            }
            return true
        }

        return false
    }
}
