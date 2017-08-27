package onuse.com.br.uberclone;

import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    private Button btnPassageiro, btnMotorista;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnMotorista = (Button)findViewById(R.id.btnMotorista);
        btnPassageiro = (Button)findViewById(R.id.btnPassageiro);

        btnMotorista.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent  intent = new Intent(MainActivity.this, MotoristaMapsActivity.class);
                startActivity(intent);
                finish();
            }
        });

        btnPassageiro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent  intent = new Intent(MainActivity.this, PassageiroMapsActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
