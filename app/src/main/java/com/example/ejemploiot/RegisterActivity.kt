package com.example.ejemploiot

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.Response
import com.android.volley.toolbox.StringRequest
import com.android.volley.toolbox.Volley
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.firebase.auth.FirebaseAuth

class RegisterActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        val etRegEmail = findViewById<TextInputEditText>(R.id.etRegEmail)
        val etRegPassword = findViewById<TextInputEditText>(R.id.etRegPassword)
        val etConfirmPassword = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnRegister = findViewById<MaterialButton>(R.id.btnRegister)
        val progressBarReg = findViewById<ProgressBar>(R.id.progressBarReg)
        val tvLoginPrompt = findViewById<TextView>(R.id.tvLoginPrompt)

        btnRegister.setOnClickListener {
            val email = etRegEmail.text.toString().trim()
            val password = etRegPassword.text.toString().trim()
            val confirmPassword = etConfirmPassword.text.toString().trim()

            if (email.isEmpty()) {
                etRegEmail.error = "Ingrese un correo electrónico"
                return@setOnClickListener
            }

            if (password.length < 6) {
                etRegPassword.error = "La contraseña debe tener al menos 6 caracteres"
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                etConfirmPassword.error = "Las contraseñas no coinciden"
                return@setOnClickListener
            }

            btnRegister.text = ""
            progressBarReg.visibility = View.VISIBLE
            btnRegister.isEnabled = false

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    progressBarReg.visibility = View.GONE
                    btnRegister.isEnabled = true
                    btnRegister.text = getString(R.string.btn_register)

                    if (task.isSuccessful) {
                        // Guardar también en MySQL en XAMPP
                        registrarUsuarioEnMySQL(email, password)
                        Toast.makeText(this, "¡Registro completado con éxito!", Toast.LENGTH_SHORT).show()
                        navigateToMain()
                    } else {
                        val errorMessage = task.exception?.localizedMessage ?: "Error desconocido en Firebase"
                        AlertDialog.Builder(this)
                            .setTitle("Error al Registrar")
                            .setMessage(errorMessage + "\n\nTip: Asegúrate de tener habilitado 'Correo electrónico/Contraseña' en la consola de Firebase Authentication.")
                            .setPositiveButton("Aceptar", null)
                            .show()
                    }
                }
        }

        tvLoginPrompt.setOnClickListener {
            finish()
        }
    }

    private fun registrarUsuarioEnMySQL(email: String, pass: String) {
        val urlEmulador = "http://10.0.2.2/api_iot/register.php"
        val urlFisico = "http://192.168.1.9/api_iot/register.php"

        enviarRegistroMySQL(urlEmulador, email, pass) { exito ->
            if (!exito) {
                enviarRegistroMySQL(urlFisico, email, pass) { _ -> }
            }
        }
    }

    private fun enviarRegistroMySQL(url: String, email: String, pass: String, onComplete: (Boolean) -> Unit) {
        try {
            val queue = Volley.newRequestQueue(this)
            val request = object : StringRequest(
                Method.POST, url,
                Response.Listener { _ ->
                    Toast.makeText(this, "¡Usuario registrado también en MySQL (XAMPP)!", Toast.LENGTH_SHORT).show()
                    onComplete(true)
                },
                Response.ErrorListener {
                    onComplete(false)
                }
            ) {
                override fun getParams(): Map<String, String> {
                    val params = HashMap<String, String>()
                    params["email"] = email
                    params["password"] = pass
                    return params
                }
            }
            queue.add(request)
        } catch (_: Exception) {
            onComplete(false)
        }
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
