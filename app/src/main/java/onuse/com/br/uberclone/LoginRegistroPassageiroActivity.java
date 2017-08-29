package onuse.com.br.uberclone;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

public class LoginRegistroPassageiroActivity extends AppCompatActivity {

    private EditText emailEDT, senhaEDT;
    private Button btnLogin, btnRegistro;

    //Busca a autenticação do firebase
    FirebaseAuth autenticacao;
    FirebaseAuth.AuthStateListener authListener;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login_registro_passageiro);

        emailEDT  = (EditText)findViewById(R.id.edtEmailPassageiro);
        senhaEDT  = (EditText)findViewById(R.id.edtSenhaPassageiro);
        btnLogin  = (Button) findViewById(R.id.btnLoginPassageiro);
        btnRegistro  = (Button) findViewById(R.id.btnRegistroPassageiro);

        //busca a instancia do firebase
        autenticacao = FirebaseAuth.getInstance();

        //Recupera a instancia do firebase listener
        authListener = new FirebaseAuth.AuthStateListener() {
            @Override
            public void onAuthStateChanged(@NonNull FirebaseAuth firebaseAuth) {
                //cria um objeto usuario se login ja efetuado
                FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();

                if(user != null)
                {
                    Intent intent = new Intent(LoginRegistroPassageiroActivity.this, PassageiroMapsActivity.class);
                    startActivity(intent);
                    finish();
                }
            }
        };

        //Açoes para quando clicar no botao de login
        btnLogin.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String email = emailEDT.getText().toString();
                final String senha = senhaEDT.getText().toString();
                autenticacao.signInWithEmailAndPassword(email, senha).addOnCompleteListener(LoginRegistroPassageiroActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(!task.isSuccessful())
                        {
                            Toast.makeText(LoginRegistroPassageiroActivity.this, "Erro de login", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        });
        //Ações para quando clicar  no botao de registro
        btnRegistro.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                final String email = emailEDT.getText().toString();
                final String senha = senhaEDT.getText().toString();
                autenticacao.createUserWithEmailAndPassword(email, senha).addOnCompleteListener(LoginRegistroPassageiroActivity.this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if(!task.isSuccessful())
                        {
                            Toast.makeText(LoginRegistroPassageiroActivity.this, "Erro de login", Toast.LENGTH_LONG).show();
                        }else{
                            String usuarioID = autenticacao.getCurrentUser().getUid();
                            DatabaseReference usuarioAtual = FirebaseDatabase.getInstance().getReference()
                                    .child("Users")
                                    .child("Passageiros")
                                    .child(usuarioID);
                            usuarioAtual.setValue(true);
                        }
                    }
                });
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        autenticacao.addAuthStateListener(authListener);
    }
    @Override
    protected void onStop() {
        super.onStop();
        autenticacao.removeAuthStateListener(authListener);
    }
}
