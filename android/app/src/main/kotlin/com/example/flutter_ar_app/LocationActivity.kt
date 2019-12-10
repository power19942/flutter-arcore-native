package com.example.flutter_ar_app

import android.content.Context;
import android.net.Uri
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.google.ar.core.Frame;
import com.google.ar.core.Plane;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.UnavailableException;
import com.google.ar.sceneform.ArSceneView;
import com.google.ar.sceneform.Node;
import com.google.ar.sceneform.rendering.ModelRenderable;
import com.google.ar.sceneform.rendering.ViewRenderable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import uk.co.appoly.arcorelocation.LocationMarker;
import uk.co.appoly.arcorelocation.LocationScene;
import uk.co.appoly.arcorelocation.rendering.LocationNode;
import uk.co.appoly.arcorelocation.rendering.LocationNodeRender;
import uk.co.appoly.arcorelocation.utils.ARLocationPermissionHelper;


class LocationActivity : AppCompatActivity() {
    private var installRequested: Boolean = false
    private var hasFinishedLoading = false

    private var loadingMessageSnackbar: Snackbar? = null

    private var arSceneView: ArSceneView? = null

    // Renderables for this example
    private var andyRenderable: ModelRenderable? = null
    private var exampleLayoutRenderable: ViewRenderable? = null

    // Our ARCore-Location scene
    private var locationScene: LocationScene? = null

    /**
     * Example node of a layout
     *
     * @return
     */
    private// Add  listeners etc here
    val exampleView: Node
        get() {
            val base = Node()
            base.setRenderable(exampleLayoutRenderable)
            val c = this
            val eView = exampleLayoutRenderable!!.view
            eView.setOnTouchListener { v, event ->
                Toast.makeText(
                        c, "Location marker touched.", Toast.LENGTH_LONG)
                        .show()
                false
            }

            return base
        }

    /***
     * Example Node of a 3D model
     *
     * @return
     */
    lateinit var andy: Node



    override// CompletableFuture requires api level 24
    fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ar)
        arSceneView = findViewById(R.id.ar_scene_view)

        // Build a renderable from a 2D View.
        val exampleLayout = ViewRenderable.builder()
                .setView(this, R.layout.example_layout)
                .build()

        // When you build a Renderable, Sceneform loads its resources in the background while returning
        // a CompletableFuture. Call thenAccept(), handle(), or check isDone() before calling get().
        val andy = ModelRenderable.builder()
                .setSource(this, Uri.parse("andy.sfb"))
                .build()


        CompletableFuture.allOf(
                exampleLayout,
                andy)
                .handle { notUsed, throwable ->
                    // When you build a Renderable, Sceneform loads its resources in the background while
                    // returning a CompletableFuture. Call handle(), thenAccept(), or check isDone()
                    // before calling get().

                    if (throwable != null) {
                        DemoUtils.displayError(this, "Unable to load renderables", throwable)
                        return@handle
                    }

                    try {
                        exampleLayoutRenderable = exampleLayout.get()
                        andyRenderable = andy.get()
                        hasFinishedLoading = true

                    } catch (ex: InterruptedException) {
                        DemoUtils.displayError(this, "Unable to load renderables", ex)
                    } catch (ex: ExecutionException) {
                        DemoUtils.displayError(this, "Unable to load renderables", ex)
                    }

                    null
                }

        // Set an update listener on the Scene that will hide the loading message once a Plane is
        // detected.
        arSceneView!!
                .scene
                .addOnUpdateListener { frameTime ->
                    if (!hasFinishedLoading) {
                        return@addOnUpdateListener
                    }

                    if (locationScene == null) {
                        // If our locationScene object hasn't been setup yet, this is a good time to do it
                        // We know that here, the AR components have been initiated.
                        locationScene = LocationScene(this, arSceneView)

                        // Now lets create our location markers.
                        // First, a layout
                        val layoutLocationMarker = LocationMarker(
                                -4.849509,
                                42.814603,
                                exampleView
                        )

                        // An example "onRender" event, called every frame
                        // Updates the layout with the markers distance
                        layoutLocationMarker.renderEvent = LocationNodeRender { node ->
                            val eView = exampleLayoutRenderable!!.view
                            val distanceTextView: TextView = eView.findViewById(R.id.textView2)
                            distanceTextView.text = node.distance.toString() + "M"
                        }
                        // Adding the marker
                        locationScene!!.mLocationMarkers.add(layoutLocationMarker)

                        // Adding a simple location marker of a 3D model
                        locationScene!!.mLocationMarkers.add(
                                LocationMarker(
                                        36.29621,
                                        33.5154431,
                                        getMyAndy()))
                    }
                    val frame = arSceneView!!.arFrame
                    if (frame == null) {
                        return@addOnUpdateListener
                    }

                    if (frame!!.camera.trackingState !== TrackingState.TRACKING) {
                        return@addOnUpdateListener
                    }

                    if (locationScene != null) {
                        locationScene!!.processFrame(frame)
                    }

                    if (loadingMessageSnackbar != null) {
                        for (plane in frame!!.getUpdatedTrackables(Plane::class.java)) {
                            if (plane.getTrackingState() === TrackingState.TRACKING) {
                                hideLoadingMessage()
                            }
                        }
                    }
                }


        // Lastly request CAMERA & fine location permission which is required by ARCore-Location.
        ARLocationPermissionHelper.requestPermission(this)
    }

    private fun getMyAndy(): Node {
        val base = Node()
        base.renderable = andyRenderable
        val c = this
        base.setOnTapListener { v, event ->
            Toast.makeText(
                    c, "Andy touched.", Toast.LENGTH_LONG)
                    .show()
        }
        return base
    }

    /**
     * Make sure we call locationScene.resume();
     */
    override fun onResume() {
        super.onResume()

        if (locationScene != null) {
            locationScene!!.resume()
        }

        if (arSceneView!!.session == null) {
            // If the session wasn't created yet, don't resume rendering.
            // This can happen if ARCore needs to be updated or permissions are not granted yet.
            try {
                val session = DemoUtils.createArSession(this, installRequested)
                if (session == null) {
                    installRequested = ARLocationPermissionHelper.hasPermission(this)
                    return
                } else {
                    arSceneView!!.setupSession(session)
                }
            } catch (e: UnavailableException) {
                DemoUtils.handleSessionException(this, e)
            }

        }

        try {
            arSceneView!!.resume()
        } catch (ex: CameraNotAvailableException) {
            DemoUtils.displayError(this, "Unable to get camera", ex)
            finish()
            return
        }

        if (arSceneView!!.session != null) {
            showLoadingMessage()
        }
    }

    /**
     * Make sure we call locationScene.pause();
     */
    public override fun onPause() {
        super.onPause()

        if (locationScene != null) {
            locationScene!!.pause()
        }

        arSceneView!!.pause()
    }

    public override fun onDestroy() {
        super.onDestroy()
        arSceneView!!.destroy()
    }

    override fun onRequestPermissionsResult(
            requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!ARLocationPermissionHelper.hasPermission(this)) {
            if (!ARLocationPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                // Permission denied with checking "Do not ask again".
                ARLocationPermissionHelper.launchPermissionSettings(this)
            } else {
                Toast.makeText(
                        this, "Camera permission is needed to run this application", Toast.LENGTH_LONG)
                        .show()
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Standard Android full-screen functionality.
            window
                    .decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun showLoadingMessage() {
        if (loadingMessageSnackbar != null && loadingMessageSnackbar!!.isShownOrQueued()) {
            return
        }

        loadingMessageSnackbar = Snackbar.make(
                this@LocationActivity.findViewById(android.R.id.content),
                "Found plane",
                Snackbar.LENGTH_INDEFINITE)
        loadingMessageSnackbar!!.getView().setBackgroundColor(-0x40cdcdce)
        loadingMessageSnackbar!!.show()
    }

    private fun hideLoadingMessage() {
        if (loadingMessageSnackbar == null) {
            return
        }

        loadingMessageSnackbar!!.dismiss()
        loadingMessageSnackbar = null
    }
}