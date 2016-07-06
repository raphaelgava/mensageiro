package br.edu.ifspsaocarlos.sdm.mensageirosdm.activity;

import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.widget.TextView;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import br.edu.ifspsaocarlos.sdm.mensageirosdm.R;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.adapter.ContactAdapter;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.model.Contact;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.network.VolleyHelper;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.service.FetchMessagesService;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.util.Connection;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.util.Constants;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.util.Helpers;
import io.realm.Realm;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class MainActivity extends AppCompatActivity implements ContactAdapter.OnContactClickListener {

    private RecyclerView recyclerView;
    private ContactAdapter contactAdapter;
    private boolean stopThread;

    @Override
    public void onContactClickListener(int position) {
        startMessageActivity(contactAdapter.getItem(position).getId());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("SDM", "onCreate:");

        TextView tvNovoContato = new TextView(this);
        tvNovoContato.setText("Sem conexão com a internet");
        setContentView(tvNovoContato);
    }

    @Override
    public void onResume(){
        super.onResume();
        final Handler handler = new Handler();

        stopThread = false;
        new Thread() {
            public void run() {
                try {
                    boolean ok = false;
                    while ((!ok) && (!stopThread)) {
                        Log.d("SDM", "trying connection:");
                        ok = Connection.connectionVerify(getBaseContext());
                        if (ok) {
                            handler.post(new Runnable() {
                                public void run() {
                                    loadUsers();
                                }
                            });
                        }
                        Thread.sleep(5000);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    @Override
    public void onPause(){
        super.onPause();
        Log.d("SDM", "onPause:");
        stopThread = true;
    }

    private void loadUsers() {
        setContentView(R.layout.activity_main);

        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
        recyclerView = (RecyclerView) findViewById(R.id.recycler);
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setHasFixedSize(false);

        checkUser();
        fetchUsers();
        startMessagesService();
    }

    private void checkUser() {
        String userId = Helpers.getUserId(this);

        if (TextUtils.isEmpty(userId)) {
            Intent intent = new Intent(this, UserActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
        }
    }

    private void fetchUsers() {
        JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.GET, Constants.SERVER_URL + Constants.CONTATO_PATH, null, new Response.Listener<JSONObject>() {

                    @Override
                    public void onResponse(JSONObject json) {
                        parseUserList(json);
                    }
                }, new Response.ErrorListener() {

                    @Override
                    public void onErrorResponse(VolleyError error) {
                        Helpers.showDialog(MainActivity.this, R.string.dialog_content_error_fetching_user);
                    }
                });

        VolleyHelper.getInstance(this).addToRequestQueue(request);
    }

    private void parseUserList(JSONObject jsonRoot) {
        List<Contact> contactList = new ArrayList<>();

        try {
            JSONArray jsonArray = jsonRoot.getJSONArray("contatos");
            Gson gson = new Gson();

            for (int i = 0; i < jsonArray.length(); i++) {
                Contact contact = gson.fromJson(jsonArray.getJSONObject(i).toString(), Contact.class);

                if (!contact.getId().equals(Helpers.getUserId(this)))
                    contactList.add(contact);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        saveContacts(contactList);
    }

    private void saveContacts(final List<Contact> contactList) {
        if (contactList != null) {
            Realm realm = Realm.getDefaultInstance();
            realm.executeTransactionAsync(new Realm.Transaction() {
                @Override
                public void execute(Realm bgRealm) {
                    bgRealm.copyToRealmOrUpdate(contactList);
                }
            }, new Realm.Transaction.OnSuccess() {
                @Override
                public void onSuccess() {
                    updateAdapter();
                }
            }, new Realm.Transaction.OnError() {
                @Override
                public void onError(Throwable error) {
                    Log.d("SDM", "onError: " + error.toString());
                    updateAdapter();
                }
            });
        }
    }

    private void updateAdapter() {
        Realm realm = Realm.getDefaultInstance();
        RealmQuery<Contact> query = realm.where(Contact.class);
        RealmResults<Contact> result = query.findAll();

        contactAdapter = new ContactAdapter(result.subList(0, result.size()), this);
        recyclerView.setAdapter(contactAdapter);
    }

    private void startMessageActivity(String recipientId) {
        Intent intent = new Intent(this, MessageActivity.class);
        intent.putExtra(Constants.SENDER_USER_KEY, recipientId);
        startActivity(intent);
    }

    private void startMessagesService() {
        Intent i = new Intent(this, FetchMessagesService.class);
        startService(i);
    }

}
