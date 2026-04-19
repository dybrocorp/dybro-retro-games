package com.dybrocorp.retrogame

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser

class AuthManager(context: Context) {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    var googleSignInClient: GoogleSignInClient
    private val prefs = context.getSharedPreferences("app_billing", Context.MODE_PRIVATE)

    init {
        // ID de cliente Web por defecto (de config en strings via google-services.json)
        val webClientId = context.getString(R.string.default_web_client_id)
        
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(webClientId)
            .requestEmail()
            .requestProfile()
            .build()
        
        googleSignInClient = GoogleSignIn.getClient(context, gso)
    }

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun isPremium(): Boolean {
        // Mock de compra de la app (no solo por estar logueado)
        return prefs.getBoolean("is_premium", false)
    }

    fun purchasePremium() {
        prefs.edit().putBoolean("is_premium", true).apply()
    }

    fun signOut(onComplete: () -> Unit) {
        auth.signOut()
        googleSignInClient.signOut().addOnCompleteListener { onComplete() }
    }
}
