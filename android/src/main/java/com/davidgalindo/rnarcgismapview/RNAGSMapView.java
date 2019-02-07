package com.davidgalindo.rnarcgismapview;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.esri.arcgisruntime.ArcGISRuntimeException;
import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.GeometryEngine;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.geometry.SpatialReferences;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.Viewpoint;
import com.esri.arcgisruntime.mapping.view.Callout;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.IdentifyGraphicsOverlayResult;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.LifecycleEventListener;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.uimanager.events.RCTEventEmitter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

public class RNAGSMapView extends LinearLayout implements LifecycleEventListener {
    // MARK: Variables/Prop declarations
    View rootView;
    public MapView mapView;
    String basemapUrl = "";
    Boolean recenterIfGraphicTapped = false;
    HashMap<String, RNAGSGraphicsOverlay> rnGraphicsOverlays = new HashMap<>();

    // MARK: Initializers
    public RNAGSMapView(Context context) {
        super(context);
        rootView = inflate(context,R.layout.rnags_mapview,this);
        mapView = rootView.findViewById(R.id.agsMapView);
        if (context instanceof ReactContext) {
            ((ReactContext) context).addLifecycleEventListener(this);
        }
        setUpMap();
    }

    @SuppressLint("ClickableViewAccessibility")
    public void setUpMap() {
        mapView.setMap(new ArcGISMap(Basemap.Type.STREETS_VECTOR, 34.057, -117.196, 17));
        mapView.setOnTouchListener(new OnSingleTouchListener(getContext(),mapView));
        mapView.getMap().addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                ArcGISRuntimeException e = mapView.getMap().getLoadError();
                Boolean success = e != null;
                String errorMessage = !success ? "" : e.getMessage();
                WritableMap map = Arguments.createMap();
                map.putBoolean("success",success);
                map.putString("errorMessage",errorMessage);
                emitEvent("onMapDidLoad",map);
            }
        });
    }

    // MARK: Prop set methods
    public void setBasemapUrl(String url) {
        basemapUrl = url;
        if(basemapUrl == null || basemapUrl.isEmpty()) {
            return;
        }
        // Set basemap of map
        if (mapView.getMap() == null) {
            setUpMap();
        }
        final Basemap basemap = new Basemap(basemapUrl);
        basemap.addDoneLoadingListener(() -> {
            if (basemap.getLoadError() != null) {
                Log.w("AGSMap", "An error occurred: " + basemap.getLoadError().getMessage());
            } else {
                mapView.getMap().setBasemap(basemap);
            }
        });
        basemap.loadAsync();
    }

    public void setRecenterIfGraphicTapped(boolean value) {
        recenterIfGraphicTapped = value;
    }

    public void setInitialMapCenter(ReadableArray initialCenter) {
        ArrayList<Point> points = new ArrayList<>();
        for (int i = 0; i < initialCenter.size(); i++) {
            ReadableMap item = initialCenter.getMap(i);
            if (item == null) {
                continue;
            }
            Double latitude = item.getDouble("latitude");
            Double longitude = item.getDouble("longitude");
            if (latitude == 0 || longitude == 0) {
                continue;
            }
            Point point = new Point(longitude, latitude, SpatialReferences.getWgs84());
            points.add(point);
        }
        // If no points exist, add a sample point
        if (points.size() == 0) {
            points.add(new Point(36.244797,-94.148060, SpatialReferences.getWgs84()));
        }
        if (points.size() == 1) {
            mapView.getMap().setInitialViewpoint(new Viewpoint(points.get(0),10000));
        } else {
            Polygon polygon = new Polygon(new PointCollection(points));
            Viewpoint viewpoint = viewpointFromPolygon(polygon);
            mapView.getMap().setInitialViewpoint(viewpoint);
        }
    }

    // MARK: Exposed RN Methods
    public void showCallout(ReadableMap args) {
        ReadableMap rawPoint = args.getMap("point");
        if (!rawPoint.hasKey("latitude") || !rawPoint.hasKey("longitude")) {
            return;
        }
        Double latitude = rawPoint.getDouble("latitude");
        Double longitude = rawPoint.getDouble("longitude");
        if (latitude == 0 || longitude == 0) {
            return;
        }
        String title = "";
        String text = "";
        Boolean shouldRecenter = false;

        if(args.hasKey("title")) {
            title = args.getString("title");
        }
        if(args.hasKey("text")) {
            text = args.getString("text");
        }
        if(args.hasKey("shouldRecenter")) {
            shouldRecenter = args.getBoolean("shouldRecenter");
        }

        // Displaying the callout
        Point agsPoint = new Point(longitude, latitude, SpatialReferences.getWgs84());
        Callout callout = mapView.getCallout();
        View calloutContent = mapView.getCallout().getContent();
        if (calloutContent == null) {
            calloutContent = inflate(getContext(),R.layout.rnags_callout_content, null);
        }
        // Set callout content
        ((TextView) calloutContent.findViewById(R.id.title)).setText(title);
        ((TextView) calloutContent.findViewById(R.id.text)).setText(text);
        callout.setContent(calloutContent);
        callout.setLocation(agsPoint);
        callout.show();

        if (shouldRecenter) {
            mapView.setViewpointCenterAsync(agsPoint);
        }
    }

    public void centerMap(ReadableArray args) {
        final ArrayList<Point> points = new ArrayList<>();
        for (int i = 0; i <  args.size(); i++) {
            ReadableMap item = args.getMap(i);
            if (item == null) {
                continue;
            }
            Double latitude = item.getDouble("latitude");
            Double longitude = item.getDouble("longitude");
            if(latitude == 0 || longitude == 0) {
                continue;
            }
            points.add(new Point(longitude, latitude, SpatialReferences.getWgs84()));
        }
        // Perform the recentering
        if (points.size() == 1) {
            mapView.setViewpointCenterAsync(points.get(0),60000);
        } else if (points.size() > 1) {
            PointCollection pointCollection = new PointCollection(points);
            Polygon polygon = new Polygon(pointCollection);
            mapView.setViewpointGeometryAsync(polygon, 50);
        }

    }

    // Layer add/remove
    public void addGraphicsOverlay(ReadableMap args) {
        GraphicsOverlay graphicsOverlay = new GraphicsOverlay();
        mapView.getGraphicsOverlays().add(graphicsOverlay);
        RNAGSGraphicsOverlay overlay = new RNAGSGraphicsOverlay(args, graphicsOverlay);
        rnGraphicsOverlays.put(overlay.getReferenceId(), overlay);
    }

    public void removeGraphicsOverlay(String removalId) {
        RNAGSGraphicsOverlay overlay = rnGraphicsOverlays.get(removalId);
        if (overlay == null) {
            Log.w("Warning (AGS)", "No overlay with the associated ID was found.");
            return;
        }
        mapView.getGraphicsOverlays().remove(overlay.getAGSGraphicsOverlay());
        rnGraphicsOverlays.remove(removalId);
    }

    // Point updates
    public void updatePointsInGraphicsOverlay(ReadableMap args) {
        if (!args.hasKey("overlayReferenceId")) {
            Log.w("Warning (AGS)", "No overlay with the associated ID was found.");
            return;
        }
        String overlayReferenceId = args.getString("overlayReferenceId");
        RNAGSGraphicsOverlay overlay = rnGraphicsOverlays.get(overlayReferenceId);
        if (overlay != null && args.hasKey("updates")){
            overlay.updateGraphics(args.getArray("updates"));
        }
    }

    public void addPointsToOverlay(ReadableMap args) {
        if (!args.hasKey("overlayReferenceId")) {
            Log.w("Warning (AGS)", "No overlay with the associated ID was found.");
            return;
        }
        String overlayReferenceId = args.getString("overlayReferenceId");
        RNAGSGraphicsOverlay overlay = rnGraphicsOverlays.get(overlayReferenceId);
        if (overlay != null && args.hasKey("points")) {
            overlay.addGraphics(args.getArray("points"));
        }
    }

    public void removePointsFromOverlay(ReadableMap args) {
        if (!args.hasKey("overlayReferenceId")) {
            Log.w("Warning (AGS)", "No overlay with the associated ID was found.");
            return;
        }
        String overlayReferenceId = args.getString("overlayReferenceId");
        RNAGSGraphicsOverlay overlay = rnGraphicsOverlays.get(overlayReferenceId);
        if (overlay != null && args.hasKey("referenceIds")) {
            overlay.removeGraphics(args.getArray("referenceIds"));
        }
    }

    // MARK: Event emitting
    public void emitEvent(String eventName, WritableMap args) {
        ((ReactContext) getContext()).getJSModule(RCTEventEmitter.class).receiveEvent(
                getId(),
                eventName,
                args
        );
    }

    // MARK: OnTouchListener
    public class OnSingleTouchListener extends DefaultMapViewOnTouchListener {
        OnSingleTouchListener(Context context, MapView mMapView){
            super(context,mMapView);
        }

        @Override
        public boolean onSingleTapConfirmed(MotionEvent e) {
            android.graphics.Point screenPoint = new android.graphics.Point(((int) e.getX()), ((int) e.getY()));
            WritableMap screenPointMap = Arguments.createMap();
            screenPointMap.putInt("x",screenPoint.x);
            screenPointMap.putInt("y",screenPoint.y);
            Point mapPoint = mMapView.screenToLocation(screenPoint);
            WritableMap mapPointMap = Arguments.createMap();
            if (mapPoint != null) {
                Point latLongPoint = ((Point) GeometryEngine.project(mapPoint, SpatialReferences.getWgs84()));
                mapPointMap.putDouble("latitude", latLongPoint.getY());
                mapPointMap.putDouble("longitude", latLongPoint.getX());
            }
            WritableMap map = Arguments.createMap();
            map.putMap("screenPoint", screenPointMap);
            map.putMap("mapPoint",mapPointMap);

            ListenableFuture<List<IdentifyGraphicsOverlayResult>> future = mMapView.identifyGraphicsOverlaysAsync(screenPoint,15, false);
            future.addDoneListener(() -> {
                try {
                    if (!future.get().isEmpty()) {
                        // We only care about the topmost result
                        IdentifyGraphicsOverlayResult futureResult = future.get().get(0);
                        List<Graphic> graphicResult = futureResult.getGraphics();
                        // More null checking >.>
                        if (!graphicResult.isEmpty()) {
                            Graphic result = graphicResult.get(0);
                            map.putString("graphicReferenceId", Objects.requireNonNull(result.getAttributes().get("referenceId")).toString());
                            if (recenterIfGraphicTapped) {
                                mapView.setViewpointCenterAsync(((Point) result.getGeometry()));
                            }
                        }
                    }
                } catch (InterruptedException | ExecutionException exception) {
                    exception.printStackTrace();
                } finally {
                    emitEvent("onSingleTap",map);
                }
            });


            return true;
        }
    }

    // MARK: Lifecycle Event Listeners
    @Override
    public void onHostResume() {
        mapView.resume();
    }

    @Override
    public void onHostPause() {
        mapView.pause();
    }

    @Override
    public void onHostDestroy() {
        mapView.dispose();
        if (getContext() instanceof ReactContext) {
            ((ReactContext) getContext()).removeLifecycleEventListener(this);
        }
    }

    // MARK: Misc.
    public Viewpoint viewpointFromPolygon(Polygon polygon) {
        Envelope envelope = polygon.getExtent();
        Double paddingWidth = envelope.getWidth() * 0.5;
        Double paddingHeight = envelope.getHeight() * 0.5;
        return new Viewpoint(new Envelope(
                envelope.getXMin() - paddingWidth, envelope.getYMax() + paddingHeight,
                envelope.getXMax() + paddingWidth, envelope.getYMin() - paddingHeight,
                SpatialReferences.getWgs84()), 0);
    }
}