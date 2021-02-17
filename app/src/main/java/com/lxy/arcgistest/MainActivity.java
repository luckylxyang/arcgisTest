package com.lxy.arcgistest;

import androidx.appcompat.app.AppCompatActivity;

import android.Manifest;
import android.annotation.SuppressLint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.esri.arcgisruntime.concurrent.ListenableFuture;
import com.esri.arcgisruntime.data.Feature;
import com.esri.arcgisruntime.data.FeatureQueryResult;
import com.esri.arcgisruntime.data.QueryParameters;
import com.esri.arcgisruntime.data.ShapefileFeatureTable;
import com.esri.arcgisruntime.geometry.Envelope;
import com.esri.arcgisruntime.geometry.Geometry;
import com.esri.arcgisruntime.geometry.GeometryType;
import com.esri.arcgisruntime.geometry.Multipart;
import com.esri.arcgisruntime.geometry.Part;
import com.esri.arcgisruntime.geometry.Point;
import com.esri.arcgisruntime.geometry.PointCollection;
import com.esri.arcgisruntime.geometry.Polygon;
import com.esri.arcgisruntime.geometry.Polyline;
import com.esri.arcgisruntime.geometry.SpatialReference;
import com.esri.arcgisruntime.layers.FeatureLayer;
import com.esri.arcgisruntime.mapping.ArcGISMap;
import com.esri.arcgisruntime.mapping.Basemap;
import com.esri.arcgisruntime.mapping.view.DefaultMapViewOnTouchListener;
import com.esri.arcgisruntime.mapping.view.Graphic;
import com.esri.arcgisruntime.mapping.view.GraphicsOverlay;
import com.esri.arcgisruntime.mapping.view.MapView;
import com.esri.arcgisruntime.mapping.view.SketchCreationMode;
import com.esri.arcgisruntime.mapping.view.SketchEditor;
import com.esri.arcgisruntime.mapping.view.SketchStyle;
import com.esri.arcgisruntime.symbology.SimpleFillSymbol;
import com.esri.arcgisruntime.symbology.SimpleLineSymbol;
import com.esri.arcgisruntime.symbology.SimpleRenderer;
import com.permissionx.guolindev.PermissionX;
import com.permissionx.guolindev.callback.RequestCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private MapView mapView;
    private FeatureLayer mFeatureLayer;
    private ArcGISMap map;
    private Button btnZoomUp;
    private Button btnNarrow;
    private Button btnDelete;
    private Button btnAdd;
    private Button btnEdit;
    private Button btnUpdate;

    private ShapefileFeatureTable shapefileFeatureTable;

    private boolean isEdit = false;

    private PointCollection mPoint;
    private SketchEditor mainSketchEditor;
    private SketchStyle mainSketchStyle;
    private GraphicsOverlay mGraphicsOverlay = new GraphicsOverlay();
    private Geometry addGeo = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mapView = findViewById(R.id.mapView);
        btnZoomUp = findViewById(R.id.zoom_up);
        btnNarrow = findViewById(R.id.zoom_narrow);
        btnDelete = findViewById(R.id.delete);
        btnUpdate = findViewById(R.id.update);
        btnAdd = findViewById(R.id.add);
        btnEdit = findViewById(R.id.edit);

        setup();
        initPermission();
        initListener();

    }

    private void initPermission() {
        PermissionX.init(this)
                .permissions(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.LOCATION_HARDWARE)
                .request(new RequestCallback() {
                    @Override
                    public void onResult(boolean allGranted, List<String> grantedList, List<String> deniedList) {
                        if (allGranted) {

                        } else {
                            loadInit();
                        }
                    }
                });

    }

    private void initListener() {
        btnUpdate.setOnClickListener(this);
        btnAdd.setOnClickListener(this);
        btnDelete.setOnClickListener(this);
        btnZoomUp.setOnClickListener(this);
        btnNarrow.setOnClickListener(this);
        btnEdit.setOnClickListener(this);

        mapView.setOnTouchListener(new DefaultMapViewOnTouchListener(this,mapView){
            @SuppressLint("ClickableViewAccessibility")
            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (!isEdit){
                    Point clickPoint = mMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
                    int tolerance = 1;
                    double mapTolerance = tolerance * mMapView.getUnitsPerDensityIndependentPixel();
                    Envelope envelope = new Envelope(clickPoint.getX() - mapTolerance, clickPoint.getY() - mapTolerance,
                            clickPoint.getX() + mapTolerance, clickPoint.getY() + mapTolerance, mMapView.getSpatialReference());
                    QueryParameters query = new QueryParameters();
                    query.setGeometry(envelope);
                    query.setSpatialRelationship(QueryParameters.SpatialRelationship.WITHIN);
                    final ListenableFuture<FeatureQueryResult> future = mFeatureLayer.selectFeaturesAsync(query, FeatureLayer.SelectionMode.NEW);

                    future.addDoneListener(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                FeatureQueryResult result = future.get();
                                Iterator<Feature> iterator = result.iterator();
                                Feature feature;
                                int counter = 0;
                                while (iterator.hasNext()) {
                                    feature = iterator.next();
                                    Geometry geometry = feature.getGeometry();
                                    if (geometry.getGeometryType() == GeometryType.POLYGON){
                                        Polygon polygon = (Polygon) geometry;
                                    }
                                    Log.e("json",geometry.toJson());
                                    mMapView.setViewpointGeometryAsync(geometry.getExtent());
                                    counter++;
                                }
                            } catch (Exception e) {
                                e.getCause();
                            }
                        }
                    });
                }else {
                    Point clickPoint = mMapView.screenToLocation(new android.graphics.Point(Math.round(e.getX()), Math.round(e.getY())));
                    if (mPoint == null){
                        mPoint = new PointCollection(clickPoint.getSpatialReference());
                    }
                    mPoint.add(clickPoint);
                    Geometry geometry = null;
                    int length = mPoint.size();
                    if (length == 1){
                        geometry = clickPoint;
                    }else if (length == 2){
                        geometry = new Polyline(mPoint);
                    }else if (length > 2){
                        geometry = new Polygon(mPoint);
                    }
                    SimpleLineSymbol lineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.GREEN, 3.0f);

                    SimpleFillSymbol simpleFillSymbol =
                            new SimpleFillSymbol(SimpleFillSymbol.Style.SOLID, Color.YELLOW, lineSymbol);
                    Graphic graphic = new Graphic(geometry, simpleFillSymbol);
                    mGraphicsOverlay.getGraphics().add(graphic);
                    addGeo = geometry;
                }
                return super.onSingleTapConfirmed(e);
            }
        });
    }

    private void loadInit() {
//        loadShapefile();
        loadShapeFile2();
    }

    private void setup() {

        // 105.892218,26.282321
        map = new ArcGISMap();
        mapView.setMap(map);
        mapView.getGraphicsOverlays().add(mGraphicsOverlay);

    }


    private void loadShapeFile2() {
//        String shpPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/test/test.shp";
        String shpPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/Pictures/test2/test.shp";
        shapefileFeatureTable = new ShapefileFeatureTable(shpPath);
        shapefileFeatureTable.loadAsync();
        shapefileFeatureTable.addDoneLoadingListener(new Runnable() {
            @Override
            public void run() {
                GeometryType gt = shapefileFeatureTable.getGeometryType();
                String name = shapefileFeatureTable.getTableName();
                mFeatureLayer = new FeatureLayer(shapefileFeatureTable);
                if (mFeatureLayer.getFullExtent() != null) {
                    mapView.setViewpointGeometryAsync(mFeatureLayer.getFullExtent());
                } else {
                    mFeatureLayer.addDoneLoadingListener(() -> {
                                mapView.setViewpointGeometryAsync(mFeatureLayer.getFullExtent());
                            }
                    );
                }
                map.getOperationalLayers().add(mFeatureLayer);

                mainSketchEditor = new SketchEditor();
                mainSketchStyle = new SketchStyle();
                mainSketchEditor.setSketchStyle(mainSketchStyle);
                mapView.setSketchEditor(mainSketchEditor);
            }
        });


        SimpleLineSymbol lineSymbol = new SimpleLineSymbol(SimpleLineSymbol.Style.SOLID, Color.RED, 1.0f);
        SimpleFillSymbol fillSymbol = new SimpleFillSymbol(SimpleFillSymbol.Style.SOLID, Color.YELLOW, lineSymbol);
        SimpleRenderer renderer = new SimpleRenderer(fillSymbol);
        mFeatureLayer.setRenderer(renderer);
        //设置选中颜色
        mFeatureLayer.setSelectionWidth(5);
        mFeatureLayer.setSelectionColor(Color.GREEN);
    }

    @Override
    protected void onResume() {
        mapView.resume();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mapView.pause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mapView.dispose();
        super.onDestroy();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.zoom_up:
                zoomUp();
                break;
            case R.id.zoom_narrow:
                zoomNarrow();
                break;
            case R.id.add:
                add();
                break;
            case R.id.edit:
                edit();
                break;
            case R.id.update:
                update();
                break;
            case R.id.delete:
                delete();
            default:
                break;
        }
    }

    private void delete() {
        final ListenableFuture<FeatureQueryResult> selectResult = mFeatureLayer.getSelectedFeaturesAsync();
        selectResult.addDoneListener(new Runnable() {
            @Override
            public void run() {
                try {
                    mFeatureLayer.getFeatureTable().deleteFeaturesAsync(selectResult.get());
                } catch (Exception e) {
                    e.getCause();
                }
            }
        });
    }


    private void add() {
        if (addGeo != null) {
            java.util.Map<String, Object> attributes = new HashMap<String, Object>();
            attributes.put("NAME", "自己画的省份");
            Feature addedFeature = mFeatureLayer.getFeatureTable().createFeature(attributes, addGeo);
            final ListenableFuture<Void> addFeatureFuture = mFeatureLayer.getFeatureTable().addFeatureAsync(addedFeature);
            addFeatureFuture.addDoneListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        addFeatureFuture.get();
                        if (addFeatureFuture.isDone()) {
                            mPoint.clear();
                            addGeo = null;
                            showToast("添加成功");
                            Log.e("xyh:", "Feature added!");
                        }
                    } catch (InterruptedException interruptedExceptionException) {
                        // 处理异常
                        showToast("添加失败");
                        Log.e("xyh:", interruptedExceptionException.getMessage());
                    } catch (ExecutionException executionException) {
                        // 处理异常
                        showToast("添加失败");
                        Log.e("xyh:", executionException.getMessage());
                    }
                }
            });
            mainSketchEditor.stop();
        }
    }

    private void update(){
        if (addGeo != null) {
            Feature feature = shapefileFeatureTable.createFeature();
            feature.setGeometry(addGeo);
            //feature.getAttributes().put("QSDWM", "测试点"); //属性修改
            final ListenableFuture<Void> addFeatureFuture = mFeatureLayer.getFeatureTable().addFeatureAsync(feature);
            addFeatureFuture.addDoneListener(new Runnable() {
                @Override
                public void run() {
                    try {
                        addFeatureFuture.get();
                        if (addFeatureFuture.isDone()) {
                            mPoint.clear();
                            addGeo = null;
                            showToast("更新成功");
                            Log.e("xyh:", "Feature added!");
                        }
                    } catch (InterruptedException interruptedExceptionException) {
                        // 处理异常
                        showToast("更新失败");
                        Log.e("xyh:", interruptedExceptionException.getMessage());
                    } catch (ExecutionException executionException) {
                        // 处理异常
                        showToast("更新失败");
                        Log.e("xyh:", executionException.getMessage());
                    }
                }
            });
        }
    }

    private void edit() {
        if (isEdit){
            mFeatureLayer.clearSelection();
            mainSketchEditor.stop();
            mainSketchEditor.start(SketchCreationMode.POLYGON);
        }else {
            mainSketchEditor.stop();
        }
        btnEdit.setText(isEdit ? "编辑" : "取消编辑");
        isEdit = !isEdit;
    }

    private void zoomUp() {
        mapView.setViewpointScaleAsync(mapView.getMapScale() * 0.5);
    }

    private void zoomNarrow() {
        mapView.setViewpointScaleAsync(mapView.getMapScale() * 2);
    }

    private void showToast(String string){
        Toast.makeText(getApplicationContext(),string,Toast.LENGTH_SHORT).show();
    }
}