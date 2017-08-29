package onuse.com.br.uberclone;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.Button;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
import com.firebase.geofire.GeoQuery;
import com.firebase.geofire.GeoQueryEventListener;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.List;

/**
 * Created by maico on 25/08/17.
 */

public class PassageiroMapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener{

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private Button logout, requisitar;
    private Boolean foiRquisitado = false;
    private Boolean motoristaEncontrado = false;

    //local de busca
    private LatLng localdeBusca;
    private Marker marcadordeBusca, marcadorMorotista;
    //Firebase
    private DatabaseReference motoristasLocalizacaoReferencia;
    private ValueEventListener motoristaLocalizacaoReferenciaOuvinte;

    private int alcance = 1;
    private String motoristaEncontradoID;

    private GeoQuery geoQuery;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_passageiro_maps);

        //vamos adicionar o mapa dentro da activity
        SupportMapFragment mapFragment = (SupportMapFragment)getSupportFragmentManager()
                .findFragmentById(R.id.mapaPassageiro);
        mapFragment.getMapAsync(this);

        logout = findViewById(R.id.deslogarPassageiro);
        requisitar = findViewById(R.id.requisitarUber);

        //ações para quando apertar o botao deslogar
        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(PassageiroMapsActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });

        //para quando botao requisitar for apertado
        requisitar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if (foiRquisitado){
                    foiRquisitado = false;
                    geoQuery.removeAllListeners();
                    if(motoristasLocalizacaoReferencia != null) {
                        motoristasLocalizacaoReferencia.removeEventListener(motoristaLocalizacaoReferenciaOuvinte);
                    }

                    if (motoristaEncontradoID != null){
                        DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Motoristas").child(motoristaEncontradoID);
                        driverRef.setValue(true);
                        motoristaEncontradoID = null;

                    }
                    motoristaEncontrado = false;
                    alcance = 1;
                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("passageiroRequisicao");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.removeLocation(userId);

                    if(marcadordeBusca != null){
                        marcadordeBusca.remove();
                    }
                    if (marcadorMorotista != null){
                        marcadorMorotista.remove();
                    }
                    requisitar.setText("Chamar Uber");

                }else{
                    foiRquisitado = true;

                    String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("passageiroRequisicao");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(userId, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                    localdeBusca = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());
                    marcadordeBusca = mMap.addMarker(new MarkerOptions().position(localdeBusca).title("Busque-me Aqui"));

                    requisitar.setText("Procurando por motoristas....");

                    ProcurarMotoristasProximos();
                }
            }
        });
    }

    private void ProcurarMotoristasProximos(){
        DatabaseReference driverLocation = FirebaseDatabase.getInstance().getReference().child("motoristasDisponiveis");

        GeoFire geoFire = new GeoFire(driverLocation);
        geoQuery = geoFire.queryAtLocation(new GeoLocation(localdeBusca.latitude, localdeBusca.longitude), alcance);
        geoQuery.removeAllListeners();

        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if (!motoristaEncontrado && foiRquisitado){
                    motoristaEncontrado = true;
                    motoristaEncontradoID = key;

                    DatabaseReference driverRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Motoristas").child(motoristaEncontradoID);
                    String customerId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                    HashMap map = new HashMap();
                    map.put("passageiropoUID", customerId);
                    driverRef.updateChildren(map);

                    getDriverLocation();
                    requisitar.setText("Cancelar....");

                }
            }

            @Override
            public void onKeyExited(String key) {

            }

            @Override
            public void onKeyMoved(String key, GeoLocation location) {

            }

            @Override
            public void onGeoQueryReady() {
                if (!motoristaEncontrado)
                {
                    alcance++;
                    ProcurarMotoristasProximos();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {

            }
        });
    }

    private void getDriverLocation(){

        motoristasLocalizacaoReferencia = FirebaseDatabase.getInstance().getReference().child("MotoristasTrabalhando").child(motoristaEncontradoID).child("l");
        motoristaLocalizacaoReferenciaOuvinte = motoristasLocalizacaoReferencia.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && foiRquisitado){
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double locationLat = 0;
                    double locationLng = 0;
                    if(map.get(0) != null){
                        locationLat = Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        locationLng = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng driverLatLng = new LatLng(locationLat,locationLng);
                    if(marcadorMorotista != null){
                        marcadorMorotista.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(localdeBusca.latitude);
                    loc1.setLongitude(localdeBusca.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(driverLatLng.latitude);
                    loc2.setLongitude(driverLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);

                    if (distance<100){
                        requisitar.setText("Motorista chegou");
                    }else{
                        requisitar.setText("Motorista encontrado: " + String.valueOf(distance));
                    }

                    marcadorMorotista = mMap.addMarker(new MarkerOptions().position(driverLatLng).title("Seu motorista"));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {
            }
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        buildGoogleApiClient();
        mMap.setMyLocationEnabled(true);
    }

    protected synchronized void buildGoogleApiClient(){
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(LocationServices.API)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onLocationChanged(Location location) {
        if(getApplicationContext()!=null){
            mLastLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
    }

    @Override
    public void onConnectionSuspended(int i) {
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
    }

    @Override
    protected void onStop() {
        super.onStop();
    }
}
