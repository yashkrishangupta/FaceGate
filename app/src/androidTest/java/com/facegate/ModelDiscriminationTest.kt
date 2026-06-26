package com.facegate

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facegate.alignment.FaceAligner
import com.facegate.pipeline.AlignedFace
import com.facegate.recognition.FaceEmbedder
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.runBlocking
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith
import org.opencv.android.OpenCVLoader

/**
 * ModelDiscriminationTest
 *
 * Goal: isolate "does the bare model + alignment discriminate between two
 * different people" from "did enrollment's 5-photo averaging / pose-skip
 * logic dilute the stored template." This test bypasses
 * AttendancePipeline.enrollStudentFromEmbeddings() entirely — no averaging,
 * no pose-skip, no quality gate. Just detect → align → embed → compare,
 * for ONE clean shot per person.
 *
 * SETUP REQUIRED before running:
 *   1. Drop two photos of two DIFFERENT real people into:
 *        src/androidTest/assets/person_a.jpg
 *        src/androidTest/assets/person_b.jpg
 *      Front camera, indoor lighting, looking straight at the camera —
 *      i.e. roughly what a normal enrollment shot looks like.
 *      (Optional) Add src/androidTest/assets/person_a_2.jpg — a SECOND,
 *      different shot of person_a — to get a same-person baseline too.
 *   2. Run as an instrumented test on a real device or emulator
 *      (Run > Run 'ModelDiscriminationTest' in Android Studio, or
 *      `./gradlew connectedDebugAndroidTest`).
 *   3. Read the printed cosine similarity values in Logcat / test output
 *      (filter logcat by tag "ModelDiscrimination").
 *
 * HOW TO READ THE RESULT:
 *   - different-person similarity close to 0–0.3  → model/alignment are
 *     fine; the averaging + pose-skip logic in enrollment is the real
 *     problem (see chat for those fixes).
 *   - different-person similarity 0.5+            → the model checkpoint
 *     or the alignment reference template doesn't discriminate well for
 *     this pair, independent of anything in the enrollment pipeline —
 *     re-check REFERENCE_LANDMARKS_112 against this exact ONNX export, or
 *     try swapping in a known-good MobileFaceNet/ArcFace checkpoint.
 *   - same-person (person_a vs person_a_2) similarity should be
 *     noticeably HIGHER than the different-person similarity — if it
 *     isn't, that's an even stronger signal the model itself is the
 *     bottleneck.
 *
 * This test intentionally does NOT assert pass/fail thresholds — there's
 * no "correct" number to hardcode here. It just prints the values so you
 * can read them and decide what's actually limiting your matching scores.
 */
@RunWith(AndroidJUnit4::class)
class ModelDiscriminationTest {

    companion object {
        private const val TAG = "ModelDiscrimination"

        @BeforeClass
        @JvmStatic
        fun initOpenCv() {
            // Must happen before any FaceAligner.align() call, same as
            // FaceGateApp.onCreate() does for the real app.
            OpenCVLoader.initLocal()
        }
    }

    /**
     * Minimal detect → align → embed for ONE bitmap. Mirrors what
     * AttendancePipeline does internally, but with no quality gate, no
     * pose check, no averaging — the rawest possible signal for this shot.
     */
    private fun embedSingleShot(bitmap: Bitmap): FloatArray {
        val detector = FaceDetection.getClient(
            FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
                .build()
        )

        val image = InputImage.fromBitmap(bitmap, 0)
        val faces = Tasks.await(detector.process(image))
        check(faces.isNotEmpty()) { "No face detected in test image — use a clearer photo." }
        val face = faces[0]

        val aligner = FaceAligner()
        val aligned = aligner.align(bitmap, face)

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val embedder = FaceEmbedder(context)
        embedder.init()

        val alignedFace = AlignedFace(aligned.alignedBitmap, bitmap)
        val embedding = runBlocking { embedder.embed(alignedFace) }
        embedder.close()

        return embedding.vector
    }

    /** Vectors are L2-normalized, so cosine similarity == dot product. */
    private fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        var dot = 0f
        for (i in a.indices) dot += a[i] * b[i]
        return dot
    }

    private fun loadTestAsset(fileName: String): Bitmap {
        val context = InstrumentationRegistry.getInstrumentation().context
        context.assets.open(fileName).use { stream ->
            return BitmapFactory.decodeStream(stream)
                ?: error("Could not decode $fileName — is it a valid jpg/png?")
        }
    }

    @Test
    fun differentPeople_singleShot_similarity() {
        val bitmapA = loadTestAsset("person_a.jpeg")
        val bitmapB = loadTestAsset("person_b.jpeg")

        val embeddingA = embedSingleShot(bitmapA)
        val embeddingB = embedSingleShot(bitmapB)

        val similarity = cosineSimilarity(embeddingA, embeddingB)

        android.util.Log.d(TAG, "person_a vs person_b (DIFFERENT people) cosine similarity = $similarity")
        println("[$TAG] person_a vs person_b (DIFFERENT people) cosine similarity = $similarity")

        // Intentionally no assertThat/threshold here — read the printed
        // value and compare it against THRESHOLD_REJECT (0.40) and
        // THRESHOLD_ACCEPT (0.60) from PipelineConfig to judge whether the
        // model's natural separation matches what those thresholds assume.
    }

    /**
     * Optional same-person baseline. Only runs meaningfully if you've added
     * person_a_2.jpg (a second, different photo of the SAME person as
     * person_a.jpg) — otherwise it'll just compare person_a to itself,
     * which will trivially be ~1.0 and isn't a useful baseline.
     */
    @Test
    fun samePerson_twoShots_similarity() {
        val bitmapA1 = loadTestAsset("person_a.jpeg")
        val bitmapA2 = loadTestAsset("person_a_2.jpeg")

        val embeddingA1 = embedSingleShot(bitmapA1)
        val embeddingA2 = embedSingleShot(bitmapA2)

        val similarity = cosineSimilarity(embeddingA1, embeddingA2)

        android.util.Log.d(TAG, "person_a vs person_a_2 (SAME person) cosine similarity = $similarity")
        println("[$TAG] person_a vs person_a_2 (SAME person) cosine similarity = $similarity")
    }
}