package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SocialMediaPostResolverTest {
    @Test
    fun instagramPhotoSelectsTheUntransformedOriginalCandidate() {
        val html = pageScript(
            """
            {
              "data": {
                "xdt_shortcode_media": {
                  "code": "PhotoCode1",
                  "media_type": 1,
                  "original_width": 1448,
                  "original_height": 1931,
                  "image_versions2": {
                    "candidates": [
                      {"url": "https://cdn.example/photo.jpg?stp=dst-jpg_e35_p1080x1080_tt6"},
                      {"url": "https://cdn.example/photo.jpg?stp=dst-jpg_e35_tt6"},
                      {"url": "https://cdn.example/photo.jpg?stp=dst-jpg_e35_s640x640_tt6"}
                    ]
                  }
                }
              }
            }
            """.trimIndent()
        )

        val result = SocialMediaPostResolver.resolveInstagramPage(html, "PhotoCode1")

        assertEquals(1, result?.size)
        assertEquals("https://cdn.example/photo.jpg?stp=dst-jpg_e35_tt6", result?.single()?.url)
        assertFalse(result?.single()?.isVideo ?: true)
    }

    @Test
    fun instagramVideoUsesVideoVersionAndKeepsCoverOnlyAsThumbnail() {
        val html = pageScript(
            """
            {
              "data": {
                "xdt_shortcode_media": {
                  "code": "ReelCode1",
                  "media_type": 2,
                  "image_versions2": {
                    "candidates": [
                      {"width": 640, "height": 1138, "url": "https://cdn.example/cover.jpg"}
                    ]
                  },
                  "video_versions": [
                    {"type": 101, "url": "https://cdn.example/reel.mp4?token=valid"}
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val result = SocialMediaPostResolver.resolveInstagramPage(html, "ReelCode1")?.single()

        assertEquals("https://cdn.example/reel.mp4?token=valid", result?.url)
        assertTrue(result?.isVideo ?: false)
        assertEquals("https://cdn.example/cover.jpg", result?.thumbnailUrl)
    }

    @Test
    fun threadsMatchesTheRequestedPostAndResolvesItsVideoVersion() {
        val html = pageScript(
            """
            {
              "data": {
                "unrelated": {
                  "media": {
                    "code": "OtherPost",
                    "user": {},
                    "media_type": 2,
                    "video_versions": [{"url": "https://cdn.example/wrong.mp4"}]
                  }
                },
                "media": {
                  "code": "TargetPost",
                  "user": {"username": "example"},
                  "media_type": 2,
                  "image_versions2": {
                    "candidates": [
                      {"width": 960, "height": 540, "url": "https://cdn.example/cover.jpg"}
                    ]
                  },
                  "video_versions": [
                    {"type": 101, "url": "https://cdn.example/video.mp4?token=valid"}
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val result = SocialMediaPostResolver.resolveThreadsPage(html, "TargetPost")?.single()

        assertEquals("https://cdn.example/video.mp4?token=valid", result?.url)
        assertTrue(result?.isVideo ?: false)
        assertEquals("https://cdn.example/cover.jpg", result?.thumbnailUrl)
    }

    @Test
    fun mixedThreadsCarouselClassifiesEachChildAndDoesNotUseTheParentCover() {
        val html = pageScript(
            """
            {
              "data": {
                "media": {
                  "code": "MixedPost",
                  "user": {"username": "example"},
                  "media_type": 8,
                  "image_versions2": {
                    "candidates": [
                      {"width": 320, "height": 320, "url": "https://cdn.example/parent-cover.jpg"}
                    ]
                  },
                  "carousel_media": [
                    {
                      "media_type": 1,
                      "original_width": 1350,
                      "original_height": 1800,
                      "image_versions2": {
                        "candidates": [
                          {"width": 1080, "height": 1440, "url": "https://cdn.example/image-small.jpg"},
                          {"width": 1350, "height": 1800, "url": "https://cdn.example/image-original.jpg"}
                        ]
                      }
                    },
                    {
                      "media_type": 2,
                      "image_versions2": {
                        "candidates": [
                          {"width": 640, "height": 360, "url": "https://cdn.example/video-cover.jpg"}
                        ]
                      },
                      "video_versions": [
                        {"type": 101, "url": "https://cdn.example/carousel-video.mp4"}
                      ]
                    }
                  ]
                }
              }
            }
            """.trimIndent()
        )

        val result = SocialMediaPostResolver.resolveThreadsPage(html, "MixedPost")

        assertEquals(2, result?.size)
        assertEquals("https://cdn.example/image-original.jpg", result?.get(0)?.url)
        assertFalse(result?.get(0)?.isVideo ?: true)
        assertEquals("https://cdn.example/carousel-video.mp4", result?.get(1)?.url)
        assertTrue(result?.get(1)?.isVideo ?: false)
        assertEquals("https://cdn.example/video-cover.jpg", result?.get(1)?.thumbnailUrl)
    }

    @Test
    fun videoWithoutAnMp4RenditionDoesNotFallBackToItsImageCover() {
        val html = pageScript(
            """
            {
              "data": {
                "media": {
                  "code": "VideoWithoutFile",
                  "user": {"username": "example"},
                  "media_type": 2,
                  "image_versions2": {
                    "candidates": [
                      {"width": 640, "height": 360, "url": "https://cdn.example/poster.jpg"}
                    ]
                  },
                  "video_versions": [
                    {"url": "https://cdn.example/poster.jpg"}
                  ]
                }
              }
            }
            """.trimIndent()
        )

        assertNull(SocialMediaPostResolver.resolveThreadsPage(html, "VideoWithoutFile"))
    }

    private fun pageScript(json: String): String =
        "<html><script type=\"application/json\">$json</script></html>"
}
