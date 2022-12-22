package dgtic.unam.oauth

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.GraphRequest
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.FacebookAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import dgtic.unam.oauth.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var activityResultLauncher: ActivityResultLauncher<Intent>
    private var callbackManager = CallbackManager.Factory.create()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        validate()
        sesiones()
    }

    private fun sesiones() {
        val preferencias =
            getSharedPreferences(getString(R.string.file_preferencia), Context.MODE_PRIVATE)
        var email: String? = preferencias.getString("email", null)
        var proovedor: String? = preferencias.getString("proveedor", null)

        if (email != null && proovedor != null) {
            opciones(email, TipoProveedor.valueOf(proovedor))
        }
    }

    private fun validate() {
        binding.updateUser.setOnClickListener {
            if (!binding.username.text.toString().isEmpty() && !binding.password.text.toString()
                    .isEmpty()
            ) {
                FirebaseAuth.getInstance().createUserWithEmailAndPassword(
                    binding.username.text.toString(),
                    binding.password.text.toString()
                ).addOnCompleteListener {
                    if (it.isComplete) {
                        try {
                            opciones(it.result?.user?.email ?: "", TipoProveedor.CORREO)
                        } catch (e: Exception) {
                            alert()
                        }
                    } else {
                        alert()
                    }
                }
            }
        }
        binding.loginbtn.setOnClickListener {
            if (!binding.username.text.toString().isEmpty() && !binding.password.text.toString()
                    .isEmpty()
            ) {
                FirebaseAuth.getInstance().signInWithEmailAndPassword(
                    binding.username.text.toString(),
                    binding.password.text.toString()
                ).addOnCompleteListener {
                    if (it.isSuccessful) {
                        opciones(it.result?.user?.email ?: "", TipoProveedor.CORREO)
                    } else {
                        // Error
                        alert()
                    }
                }
            }
        }
        iniciarActividad()
        binding.google.setOnClickListener {
            val conf =
                GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN).requestIdToken(
                    getString(R.string.default_web_client_id)
                )
                    .requestEmail()
                    .build()
            val clienteGoogle = GoogleSignIn.getClient(this, conf)
            clienteGoogle.signOut()
            val signIn: Intent = clienteGoogle.signInIntent
            activityResultLauncher.launch(signIn)
        }
        FirebaseAuth.getInstance().signOut()
        LoginManager.getInstance().logOut()
        binding.facebook.setReadPermissions(
            listOf(
                "public_profile",
                "email",
                "user_birthday",
                "user_friends",
                "user_gender"
            )
        )
        binding.facebook.registerCallback(callbackManager, object : FacebookCallback<LoginResult> {
            override fun onSuccess(result: LoginResult) {
                Log.e("TAG", "login")
                val request = GraphRequest.newMeRequest(result.accessToken)
                { _ , _ ->
                    val token = result.accessToken
                    val credenciales = FacebookAuthProvider.getCredential(token.token)
                    FirebaseAuth.getInstance().signInWithCredential(credenciales)
                        .addOnCompleteListener {
                            if (it.isSuccessful) {
                                Toast.makeText(
                                    binding.signin.context,
                                    "Sign in successful",
                                    Toast.LENGTH_SHORT
                                )
                                    .show()
                                opciones(it.result?.user?.email ?: "", TipoProveedor.FACEBOOK)
                            } else {
                                alert()
                            }
                        }
                }
                val parameters = Bundle()
                parameters.putString(
                    "fields",
                    "id,name,email,gender,birthday"
                )
                request.parameters = parameters
                request.executeAsync()
            }

            override fun onCancel() {
                Log.v("TAG", "cancel")
            }

            override fun onError(error: FacebookException) {
                Log.v("TAG", error.cause.toString())
            }
        })
    }


    private fun alert() {
        val bulder = AlertDialog.Builder(this)
        bulder.setTitle("Mensaje")
        bulder.setMessage("Se produjo un error, contacte al proveedor")
        bulder.setPositiveButton("Aceptar", null)
        val dialog: AlertDialog = bulder.create()
        dialog.show()
    }

    private fun opciones(email: String, proovedor: TipoProveedor) {
        var pasos = Intent(this, OpcionesActivity::class.java).apply {
            putExtra("email", email)
            putExtra("proveedor", proovedor.name)
        }
        startActivity(pasos)
    }

    override fun onStart() {
        super.onStart()
        binding.layoutAcceso.visibility = View.VISIBLE
    }

    private fun iniciarActividad() {
        activityResultLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == Activity.RESULT_OK) {
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        Toast.makeText(this, "Sign in successful", Toast.LENGTH_SHORT).show()
                        if (account != null) {
                            val credenciales =
                                GoogleAuthProvider.getCredential(account.idToken, null)
                            FirebaseAuth.getInstance().signInWithCredential(credenciales)
                                .addOnCompleteListener {
                                    if (it.isSuccessful) {
                                        opciones(account.email ?: "", TipoProveedor.GOOGLE)
                                    } else {
                                        alert()
                                    }
                                }
                        }
                    } catch (e: ApiException) {
                        Toast.makeText(this, "Sign in failed: " + e.statusCode, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        callbackManager.onActivityResult(resultCode, resultCode, data)
    }


}