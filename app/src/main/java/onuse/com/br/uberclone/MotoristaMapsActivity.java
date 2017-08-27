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
import android.widget.Toast;

import com.firebase.geofire.GeoFire;
import com.firebase.geofire.GeoLocation;
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

import java.util.List;

public class MotoristaMapsActivity extends FragmentActivity implements OnMapReadyCallback, GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener, com.google.android.gms.location.LocationListener{

    private GoogleMap mMap;
    GoogleApiClient mGoogleApiClient;
    Location mLastLocation;
    LocationRequest mLocationRequest;
    private Button logout;
    private String passageiroID = "";
    private Boolean estaDeslogado = false;
    private Marker marcadordeBusca;
    private DatabaseReference passageirosLocalizacaoReferencia;
    private ValueEventListener passageirosLocalizacaoReferenciaOuvinte;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_motorista_maps);

        //instancia o mapa dentro do fragment, e notifica quando estiver pronto
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);


        logout = findViewById(R.id.logout);
        logout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                estaDeslogado = true;

                LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, MotoristaMapsActivity.this);
                String userId = FirebaseAuth.getInstance().getCurrentUser().getUid();
                DatabaseReference ref = FirebaseDatabase.getInstance().getReference("motoristasDisponiveis");

                GeoFire geoFire = new GeoFire(ref);
                geoFire.removeLocation(userId);

                FirebaseAuth.getInstance().signOut();
                Intent intent = new Intent(MotoristaMapsActivity.this, LoginRegistroActivity.class);
                startActivity(intent);
                finish();
            }
        });

        getAssignedCustomer();
    }

    private void getAssignedCustomer(){
        String motoristaID = FirebaseAuth.getInstance().getCurrentUser().getUid();
        DatabaseReference referencia =  FirebaseDatabase.getInstance().getReference()
                .child("Users")
                .child("Motoistas")
                .child(motoristaID)
                .child("PassageiroID");
        referencia.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists()){
                    passageiroID = dataSnapshot.getValue().toString();
                    PegarLocalizacaoDesignada();
                }else{
                    passageiroID = "";
                    if(marcadordeBusca != null)
                    {
                        marcadordeBusca.remove();
                    }
                    if(passageirosLocalizacaoReferenciaOuvinte != null)
                    {
                        passageirosLocalizacaoReferencia.removeEventListener(passageirosLocalizacaoReferenciaOuvinte);
                    }
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {

            }
        });
    }

    private void PegarLocalizacaoDesignada(){
        passageirosLocalizacaoReferencia = FirebaseDatabase.getInstance().getReference().child("PassageiroRequisicao").child(passageiroID).child("l");
        passageirosLocalizacaoReferenciaOuvinte = passageirosLocalizacaoReferencia.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {
                if(dataSnapshot.exists() && !passageiroID.equals(""))
                {
                    List<Object>map = (List<Object>) dataSnapshot.getValue();
                    double latitude = 0;
                    double longitude = 0;
                    if(map.get(0) != null){
                        latitude =  Double.parseDouble(map.get(0).toString());
                    }
                    if(map.get(1) != null){
                        longitude = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng motoristaLocalizacao = new LatLng(latitude, longitude);
                    marcadordeBusca = mMap.addMarker(new MarkerOptions().position(motoristaLocalizacao).title("Localização de ser buscada"));
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
            ActivityCompat.requestPermissions(this, new String[] {android.Manifest.permission.ACCESS_FINE_LOCATION}, 123);
            return;
        }
        buildGoogleApiClient();
        try{
            mMap.setMyLocationEnabled(true);
        }catch (SecurityException e){}
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, @NonNull final String[] permissions, @NonNull final int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 123) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.map);
                mapFragment.getMapAsync(this);
            } else {
                //Quando o usuario recusar a permissao
                Toast.makeText(this, "Você não tem permissão para acessar o gps", Toast.LENGTH_LONG).show();
            }
        }
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
        if(getApplicationContext() !=null){
            mLastLocation = location;

            LatLng latLng = new LatLng(location.getLatitude(), location.getLongitude());

            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));

            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));

            String passageiroUID = FirebaseAuth.getInstance().getCurrentUser().getUid();

            DatabaseReference refDisponivel = FirebaseDatabase.getInstance().getReference().child("MotoristasDisponiveis");

            DatabaseReference refTrabalhando = FirebaseDatabase.getInstance().getReference().child("MotoristasTrabalhando");

            GeoFire geofireDispoivel = new GeoFire(refDisponivel);
            GeoFire geofireTrabalhando = new GeoFire(refTrabalhando);

            switch (passageiroID){
                case "":
                    geofireTrabalhando.removeLocation(passageiroUID);
                    geofireDispoivel.setLocation(passageiroUID, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;
                default:
                    geofireDispoivel.removeLocation(passageiroUID);
                    geofireTrabalhando.setLocation(passageiroUID, new GeoLocation(location.getLatitude(), location.getLongitude()));
                    break;
            }
        }
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000); //setar intervalo de solicitações
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY); //setar prioridade de solicitações
        try{
            //para objetos que usam localização e permissoes perigosas precisamos circundar com try catch secutyty
            LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        }catch (SecurityException e){}
    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
