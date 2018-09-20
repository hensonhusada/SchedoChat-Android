package com.promah.schedo.schedochat;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;

import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.FirebaseException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

import java.util.concurrent.TimeUnit;

public class LoginActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = "LoginActivity";
    private static final String KEY_VERIFY_IN_PROGRESS = "key_verify_in_progress";

    private static final int STATE_INITIALIZED = 1;
    private static final int STATE_CODE_SENT = 2;
    private static final int STATE_VERIFY_FAILED = 3;
    private static final int STATE_VERIFY_SUCCESS = 4;
    private static final int STATE_SIGN_IN_FAILED = 5;
    private static final int STATE_SIGN_IN_SUCCESS = 6;

    private FirebaseAuth mAuth;

    private boolean mVerificationInProgress = false;
    private String mVerificationId;
    private PhoneAuthProvider.ForceResendingToken mResendToken;
    private PhoneAuthProvider.OnVerificationStateChangedCallbacks mCallbacks;

    private View mProgressView;
    private View mPhoneAuthView;
    private View mCodeVerificationView;

    private EditText mPhoneAuthField;
    private EditText mCodeVerificationField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        if (savedInstanceState != null) {
            onRestoreInstanceState(savedInstanceState);
        }

        mProgressView = findViewById(R.id.progress_bar_field);
        mPhoneAuthView = findViewById(R.id.phone_auth_field);
        mCodeVerificationView = findViewById(R.id.code_verification_field);

        mPhoneAuthField = findViewById(R.id.field_phone_auth);
        mCodeVerificationField = findViewById(R.id.field_code_verification);

        Button mSignInButton = findViewById(R.id.button_sign_in);
        Button mVerifyButton = findViewById(R.id.button_verify);
        Button mResendButton = findViewById(R.id.button_resend);

        mSignInButton.setOnClickListener(this);
        mVerifyButton.setOnClickListener(this);
        mResendButton.setOnClickListener(this);

        mAuth = FirebaseAuth.getInstance();

        mCallbacks = new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override
            public void onVerificationCompleted(PhoneAuthCredential phoneAuthCredential) {
                Log.d(TAG, "onVerificationCompleted:" + phoneAuthCredential);
                mVerificationInProgress = false;
                updateUI(STATE_VERIFY_SUCCESS, phoneAuthCredential);
                signInWithPhoneAuthCredential(phoneAuthCredential);
            }

            @Override
            public void onVerificationFailed(FirebaseException e) {
                Log.w(TAG, "onVerificationFailed", e);
                mVerificationInProgress = false;
                if (e instanceof FirebaseAuthInvalidCredentialsException) {
                    mPhoneAuthField.setError("Invalid phone number.");
                } else if (e instanceof FirebaseTooManyRequestsException) {
                    Snackbar.make(findViewById(android.R.id.content), "Quota exceeded.",
                            Snackbar.LENGTH_SHORT).show();
                } updateUI(STATE_VERIFY_FAILED);
            }

            @Override
            public void onCodeSent(String verificationId,
                                   PhoneAuthProvider.ForceResendingToken token) {
                Log.d(TAG, "onCodeSent:" + verificationId);
                mVerificationId = verificationId;
                mResendToken = token;
                updateUI(STATE_CODE_SENT);
            }
        };
    }

    @Override
    public void onStart() {
        super.onStart();
        FirebaseUser currentUser = mAuth.getCurrentUser();
        updateUI(currentUser);

        if (mVerificationInProgress && validatePhoneNumber()) {
            startPhoneNumberVerification("+62" + mPhoneAuthField.getText().toString());
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_VERIFY_IN_PROGRESS, mVerificationInProgress);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        mVerificationInProgress = savedInstanceState.getBoolean(KEY_VERIFY_IN_PROGRESS);
    }

    private void startPhoneNumberVerification(String phoneNumber) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(phoneNumber, 60, TimeUnit.SECONDS,
                this, mCallbacks);
        mVerificationInProgress = true;
    }

    private void verifyPhoneNumberWithCode(String verificationId, String code) {
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(verificationId, code);
        signInWithPhoneAuthCredential(credential);
    }

    private void resendVerificationCode(String phoneNumber,
                                        PhoneAuthProvider.ForceResendingToken token) {
        PhoneAuthProvider.getInstance().verifyPhoneNumber(phoneNumber,60, TimeUnit.SECONDS,
                this, mCallbacks, token);
    }

    private void signInWithPhoneAuthCredential(PhoneAuthCredential credential) {
        mAuth.signInWithCredential(credential)
                .addOnCompleteListener(this, new OnCompleteListener<AuthResult>() {
                    @Override
                    public void onComplete(@NonNull Task<AuthResult> task) {
                        if (task.isSuccessful()) {
                            Log.d(TAG, "signInWithCredential:success");
                            FirebaseUser user = task.getResult().getUser();
                            updateUI(STATE_SIGN_IN_SUCCESS, user);
                        } else {
                            Log.w(TAG, "signInWithCredential:failure", task.getException());
                            if (task.getException() instanceof FirebaseAuthInvalidCredentialsException) {
                                mCodeVerificationField.setError("Invalid code.");
                            }
                            updateUI(STATE_SIGN_IN_FAILED);
                        }
                    }
                });
    }

    private void updateUI(int uiState) {
        updateUI(uiState, mAuth.getCurrentUser(), null);
    }

    private void updateUI(FirebaseUser user) {
        if (user != null) {
            updateUI(STATE_SIGN_IN_SUCCESS, user);
        } else {
            updateUI(STATE_INITIALIZED);
        }
    }

    private void updateUI(int uiState, FirebaseUser user) {
        updateUI(uiState, user, null);
    }

    private void updateUI(int uiState, PhoneAuthCredential cred) {
        updateUI(uiState, null, cred);
    }

    private void updateUI(int uiState, FirebaseUser user, PhoneAuthCredential cred) {
        switch (uiState) {
            case STATE_INITIALIZED:
                mPhoneAuthView.setVisibility(View.VISIBLE);
                mCodeVerificationView.setVisibility(View.GONE);
                break;
            case STATE_CODE_SENT:
                mPhoneAuthView.setVisibility(View.GONE);
                mCodeVerificationView.setVisibility(View.VISIBLE);
                Toast.makeText(LoginActivity.this, R.string.status_code_sent,
                        Toast.LENGTH_LONG).show();
                break;
            case STATE_VERIFY_FAILED:
                mPhoneAuthView.setVisibility(View.VISIBLE);
                mCodeVerificationView.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, R.string.status_verification_failed,
                        Toast.LENGTH_LONG).show();
                break;
            case STATE_VERIFY_SUCCESS:
                if (cred != null) {
                    if (cred.getSmsCode() != null) {
                        mCodeVerificationField.setText(cred.getSmsCode());
                    } else {
                        mCodeVerificationField.setText(R.string.status_instant_validation);
                    }
                }

                break;
            case STATE_SIGN_IN_FAILED:
                mPhoneAuthView.setVisibility(View.VISIBLE);
                mCodeVerificationView.setVisibility(View.GONE);
                Toast.makeText(LoginActivity.this, R.string.status_sign_in_failed,
                        Toast.LENGTH_LONG).show();
                break;
            case STATE_SIGN_IN_SUCCESS:
                break;
        }

        if (user != null) {
            Intent intent = new Intent(getApplicationContext(), MainActivity.class);
            startActivity(intent); finish();
            Log.d(TAG, "debugTest:1");
        }
    }

    private boolean validatePhoneNumber() {
        String phoneNumber = "+62" + mPhoneAuthField.getText().toString();
        if (TextUtils.isEmpty(phoneNumber)) {
            mPhoneAuthField.setError("Invalid phone number.");
            return false;
        }

        return true;
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);
        mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
        mProgressView.animate().setDuration(shortAnimTime).alpha(
                show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            }
        });
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_sign_in:
                if (!validatePhoneNumber()) {
                    return;
                } startPhoneNumberVerification("+62" + mPhoneAuthField.getText().toString());
                break;
            case R.id.button_verify:
                String code = mCodeVerificationField.getText().toString();
                if (TextUtils.isEmpty(code)) {
                    mCodeVerificationField.setError("Cannot be empty.");
                    return;
                } verifyPhoneNumberWithCode(mVerificationId, code);
                break;
            case R.id.button_resend:
                resendVerificationCode("+62" + mPhoneAuthField.getText().toString(), mResendToken);
                break;
        }
    }
}
