package onuse.com.br.uberclone;

import android.content.Intent;
import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
            public void onClick(View view) {
                //se o motorista foi requisitado
                if (foiRquisitado) {
                    foiRquisitado = false;
                    geoQuery.removeAllListeners();
                    motoristasLocalizacaoReferencia.removeEventListener(motoristaLocalizacaoReferenciaOuvinte);

                    if (motoristaEncontradoID != null) {
                        DatabaseReference motoristaRef = FirebaseDatabase.getInstance().getReference().child("Users").child("Drivers").child(motoristaEncontradoID);
                        motoristaRef.setValue(true);
                        motoristaEncontradoID = null;
                    }
                    motoristaEncontrado = false;
                    alcance = 1;
                    String usuarioID = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("passageiroRequisicao");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.removeLocation(usuarioID);

                    if (marcadordeBusca != null) {
                        marcadordeBusca.remove();
                    }
                    if (marcadorMorotista != null) {
                        marcadorMorotista.remove();
                    }
                    requisitar.setText("Chamar Uber");
                } else {
                    foiRquisitado = true;

                    String UsuarioID = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    DatabaseReference ref = FirebaseDatabase.getInstance().getReference("passageiroRequisicao");
                    GeoFire geoFire = new GeoFire(ref);
                    geoFire.setLocation(UsuarioID, new GeoLocation(mLastLocation.getLatitude(), mLastLocation.getLongitude()));

                    //Criamos um novo marcador com nossa posiçção atual
                    localdeBusca = new LatLng(mLastLocation.getLatitude(), mLastLocation.getLongitude());

                    //com o novo marcador adicionamos ao mapa e configuramos as opções
                    marcadordeBusca = mMap.addMarker(new MarkerOptions().position(localdeBusca).title("Busque-me Aqui"));

                    //botao de requisisao mudamos o texto
                    requisitar.setText("Procurando por motoristas...");

                    //chamamos o metodo de motoristas proximos
                    //MotoristasProximos();
                    MotoristasProximos();
                }

            }
        });
    }

    private void MotoristasProximos(){
        //adiciona no nó de motoristas disponiveis
        DatabaseReference motoristaLocalizacao = FirebaseDatabase.getInstance().getReference()
                .child("motoristasDisponiveis");

        //criamos um objeto geofire com a nossa referencia do firebase
        GeoFire geoFire = new GeoFire(motoristaLocalizacao);

        //criamos uma requisição de busca dentro de um alcançe para retornar a latitude e longitude
        geoQuery = geoFire.queryAtLocation(new GeoLocation(localdeBusca.latitude, localdeBusca.longitude), alcance);
        //remover os ouvintes se ouver algum
        geoQuery.removeAllListeners();

        //adicionaremos o novo ouvinte
        geoQuery.addGeoQueryEventListener(new GeoQueryEventListener() {
            @Override
            public void onKeyEntered(String key, GeoLocation location) {
                if(!motoristaEncontrado && foiRquisitado)
                {
                    //motoristas encontrado recebe verdadeiro e a chave da id passado pela requisição
                    motoristaEncontrado = true;
                    motoristaEncontradoID = key;

                    DatabaseReference motoristaReferencia = FirebaseDatabase.getInstance().getReference()
                            .child("Users")
                            .child("Motoristas")
                            .child(motoristaEncontradoID);

                    String passageiroUID = FirebaseAuth.getInstance().getCurrentUser().getUid();

                    HashMap map = new HashMap();
                    map.put("passageiropoUID", passageiroUID);
                    motoristaReferencia.updateChildren(map);

                    LocalizacaoMotorista();
                    requisitar.setText("Procurando por motoristas...");
                }
            }

            @Override
            public void onKeyExited(String key) {}

            @Override
            public void onKeyMoved(String key, GeoLocation location) {}

            @Override
            public void onGeoQueryReady() {
                //se motorista nao for encontrado naquele raio adiciona +1 para tentar novamente
                if (!motoristaEncontrado)
                {
                    alcance++;
                    MotoristasProximos();
                }
            }

            @Override
            public void onGeoQueryError(DatabaseError error) {}
        });
    }

    private void LocalizacaoMotorista(){
        motoristasLocalizacaoReferencia = FirebaseDatabase.getInstance().getReference()
                .child("MotoristasTrabalhando")
                .child(motoristaEncontradoID)
                .child("l");
        motoristaLocalizacaoReferenciaOuvinte = motoristasLocalizacaoReferencia.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(DataSnapshot dataSnapshot) {

                if (dataSnapshot.exists() && foiRquisitado) {
                    //Criamos uma lista de objetos chamado map
                    List<Object> map = (List<Object>) dataSnapshot.getValue();
                    double latitude = 0;
                    double longitude = 0;

                    if (map.get(0) != null) {
                        latitude = Double.parseDouble(map.get(0).toString());
                    }
                    if (map.get(1) != null) {
                        longitude = Double.parseDouble(map.get(1).toString());
                    }
                    LatLng motoristaLatLng = new LatLng(latitude, longitude);
                    if (marcadorMorotista != null) {
                        marcadorMorotista.remove();
                    }
                    Location loc1 = new Location("");
                    loc1.setLatitude(localdeBusca.latitude);
                    loc1.setLongitude(localdeBusca.longitude);

                    Location loc2 = new Location("");
                    loc2.setLatitude(motoristaLatLng.latitude);
                    loc2.setLongitude(motoristaLatLng.longitude);

                    float distance = loc1.distanceTo(loc2);

                    if (distance < 100) {
                        requisitar.setText("Motorista Chegou");
                    } else {
                        requisitar.setText("Motorista a distância de: " + String.valueOf(distance));
                    }

                    //mostra o local onde  o motorista esta nesse momento
                    marcadorMorotista = mMap.addMarker(new MarkerOptions().position(motoristaLatLng).title("Seu Motorista"));
                }
            }

            @Override
            public void onCancelled(DatabaseError databaseError) {}
        });

    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;

        buildGoogleApiClient();
        try{
            mMap.setMyLocationEnabled(true);
        }catch (SecurityException e){}
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
    public void onConnected(@Nullable Bundle bundle) {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(1000);
        mLocationRequest.setFastestInterval(1000);
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);

       try{
           LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
       }catch(SecurityException e){}

    }

    @Override
    public void onConnectionSuspended(int i) {}

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {}

    @Override
    public void onLocationChanged(Location location) {
        if(getApplicationContext()!=null){
            mLastLocation = location;
//novo comando adicionado
            LatLng latLng = new LatLng(location.getLatitude(),location.getLongitude());
            //atualizar a camera para nossa ultuma posição
            mMap.moveCamera(CameraUpdateFactory.newLatLng(latLng));
            mMap.animateCamera(CameraUpdateFactory.zoomTo(11));
        }
    }
}
