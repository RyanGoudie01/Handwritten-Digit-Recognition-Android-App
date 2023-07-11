package com.example.honoursproject

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.result.ActivityResultCallback
import androidx.appcompat.app.AppCompatActivity
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageView
import java.io.IOException


class MainActivity : AppCompatActivity() {

    private lateinit var imageView: CropImageView
    private lateinit var predictedTextView: TextView
    private lateinit var imageBitmap: Bitmap
    private var digitClassifier = DigitClassifier(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        imageView = findViewById(R.id.ImageView)
        predictedTextView = findViewById(R.id.predicted_text)
        val takePhotoButton: Button = findViewById(R.id.take_photo_button)
        val cropButton: Button = findViewById(R.id.crop_button)
        val convertButton: Button = findViewById(R.id.convert_button)
        val predictButton: Button = findViewById(R.id.predict_button)
        val clearButton: Button = findViewById(R.id.clear_button)
        digitClassifier.initialize()
        var photoTaken: Boolean = false // Sets value for error handling

        // Captures Live image using the smartphone Camera
        takePhotoButton.setOnClickListener(View.OnClickListener {
            var camera: Intent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            startActivityForResult(camera, 200)
            photoTaken = true // if photo is taken = true
        })

        // Calls the crop function when the crop button is pressed
        cropButton.setOnClickListener {
            // if photo is captured then run normally
            if (photoTaken) {
                cropImage(imageBitmap)
                // Crop library contains scale bug so feed back solution to user to realign before
                // Conversion.
                predictedTextView?.text = getString(R.string.crop_realign_message)
            } else { // if no photo is captured handle error and display text to user
                // Update the message textview with a message
                predictedTextView?.text = getString(R.string.null_image)
            }
        }


        // Calls the convert function when the convert button is pressed
        convertButton.setOnClickListener {
            // if photo is captured then run normally
            if (photoTaken) {
                convertImage(imageBitmap)
            } else { // if no photo is captured handle error and display text to user
                // Update the message textview with a message
                predictedTextView?.text = getString(R.string.null_image)
            }
        }


        // Calls the classify function when the predict button is pressed
        predictButton.setOnClickListener(View.OnClickListener {
            // if photo is captured then run normally
            if (photoTaken) {
                classifyImage()
            } else { // if no photo is captured handle error and display text to user
                // Update the message textview with a message
                predictedTextView?.text = getString(R.string.null_image)
            }
        })

        // Calls the clear function when the clear button is pressed
        clearButton.setOnClickListener {
            clearImageAndResult()
            photoTaken = false
        }
    }

    // After the camera activity is complete this function sets the bitmap to the image view
    // and the variable value.
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 200 && resultCode == Activity.RESULT_OK) {
            imageBitmap = data?.extras?.get("data") as Bitmap
            imageView.setImageBitmap(imageBitmap)
            predictedTextView?.text = getString(R.string.crop_message)
        }
    }

    // Using the captured image this uses the CanHub library to allow the user to crop the image
    // Into a smaller version and to single out the image to be processed by the rest of the app.
    private fun cropImage(bitmap: Bitmap?){
            val cropped: Bitmap = imageView.getCroppedImage() !!
            imageBitmap = cropped
            imageView.setImageBitmap(cropped)
    }


    // When this function is called it passes the cropped image into Otsu's method to then separate
    // the image into the background and foreground , this value is then passed into the image
    // processing function with the cropped image to then convert the pixels into their coloured
    // values to create a black background and a white digit. This processed image is then displayed.
    private fun convertImage(bitmap: Bitmap){
        val thresholdValue = calculateThreshold(imageBitmap)
        val processedImage = applyThreshold(imageBitmap,thresholdValue)
        imageBitmap = processedImage
        imageView.setImageBitmap(imageBitmap)
        predictedTextView?.text = getString(R.string.predict_message)
    }


    // Clears the prediction and image
    private fun clearImageAndResult() {
        imageView.setImageBitmap(null)
        predictedTextView?.text = getString(R.string.prediction_text_placeholder)

    }

    // This function is a implementation of Otsu's method for calculating the threshold value
    // This method is used to separate the image into foreground and background classes.
    private fun calculateThreshold(bitmap: Bitmap): Int {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)

        val histogram = IntArray(256)
        for (pixel in pixels) {
            val intensity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
            histogram[intensity]++
        }

        var sum = 0
        var sumB = 0
        var wB = 0
        var wF: Int
        var max = 0
        var threshold = 0

        for (i in 0 until 256) {
            wB += histogram[i]
            if (wB == 0) continue
            wF = pixels.size - wB
            if (wF == 0) break

            sumB += i * histogram[i]
            val mB = sumB / wB
            val mF = (sum - sumB) / wF
            val between = wB * wF * (mB - mF) * (mB - mF)
            if (between > max) {
                max = between
                threshold = i
            }
        }

        return threshold
    }


    // Function is used to convert the captured image into a high contrast bitmap image
    // The function then uses that image and converts the image pixels into white or black dependent
    // on the threshold
    private fun applyThreshold(bitmap: Bitmap, threshold: Int): Bitmap {
        val thresholdImage = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)

        for (x in 0 until imageBitmap.width) {
            for (y in 0 until imageBitmap.height) {
                val pixel = imageBitmap.getPixel(x, y)
                val intensity = (Color.red(pixel) + Color.green(pixel) + Color.blue(pixel)) / 3
                if (intensity >= threshold) {
                    thresholdImage.setPixel(x, y, Color.WHITE)
                } else {
                    thresholdImage.setPixel(x, y, Color.BLACK)
                }
            }
        }
        for (y in 0 until thresholdImage.height) {
            for (x in 0 until thresholdImage.width) {
                val pixel = thresholdImage.getPixel(x, y)
                thresholdImage.setPixel(x, y, Color.argb(255, 255 - Color.red(pixel), 255 - Color.green(pixel), 255 - Color.blue(pixel)))
            }
        }

        return thresholdImage
    }

    // Call the Digit Classifier class upon requirements being made
    private fun classifyImage() {
        if ((imageBitmap!= null) && (digitClassifier.isInitialized)) {
            digitClassifier
                .classifyAsync(imageBitmap)
                .addOnSuccessListener { resultText -> predictedTextView?.text = resultText }
                .addOnFailureListener { e ->
                    predictedTextView?.text = getString(
                        R.string.classification_error_message,
                        e.localizedMessage
                    )
                    Log.e(TAG, "Classification Error.", e)
                }
        }
    }
    companion object {
        private const val TAG = "MainActivity"
    }


}



