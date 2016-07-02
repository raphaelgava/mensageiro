package br.edu.ifspsaocarlos.sdm.mensageirosdm.activity;

import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.EditText;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import br.edu.ifspsaocarlos.sdm.mensageirosdm.R;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.adapter.MessageAdapter;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.model.Contact;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.model.Message;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.util.Constants;
import br.edu.ifspsaocarlos.sdm.mensageirosdm.util.Helpers;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

public class MessageActivity extends AppCompatActivity implements OnClickListener {
    private EditText editTextMessage;
    private FloatingActionButton buttonSend;
    private RecyclerView recyclerView;
    private MessageAdapter adapter;

    private String contactId;
    private String userId;

    private Realm realm;
    private RealmResults<Message> resultMessages;
    private List<Message> messageList;

    private RequestQueue requestQueue;

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                onBackPressed();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.fab:
                sendMenssage();
                break;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message);

        // setup volley
        requestQueue = Volley.newRequestQueue(this);

        // setup realm
        realm = Realm.getDefaultInstance();

        // current user
        userId = Helpers.getUserId(this);

        // destinatário
        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            contactId = extras.getString(Constants.SENDER_USER_KEY);
        }

        Contact contact = realm.where(Contact.class).equalTo("id", contactId).findFirst();

        // setup toolBar
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setTitle(contact.getNome_completo());


        // bind views
        recyclerView = (RecyclerView) findViewById(R.id.recycler);
        editTextMessage = (EditText) findViewById(R.id.edit_message);
        buttonSend = (FloatingActionButton) findViewById(R.id.fab);
        buttonSend.setOnClickListener(this);


        // setup recycler
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(this);
        recyclerView.setLayoutManager(mLayoutManager);
        recyclerView.setHasFixedSize(false);
        messageList = new ArrayList<>();


        // check messages
        if (Helpers.isCurrentUserSentMessagesSynchronized(this, contactId)) {
            setupQuery();
        } else {
            fetchUserSentMessages();
        }
    }

    private void setupQuery() {
        resultMessages = realm.where(Message.class)
                .equalTo("destino_id", userId)
                .equalTo("origem_id", contactId)
                .or()
                .equalTo("destino_id", contactId)
                .equalTo("origem_id", userId)
                .findAll();

        resultMessages.addChangeListener(new RealmChangeListener<RealmResults<Message>>() {
            @Override
            public void onChange(RealmResults<Message> element) {
                if (element.size() > messageList.size()) {
                    updateAdapter(element);
                }
            }
        });

        setupAdapter();
    }

    private void setupAdapter() {
        for (int i = 0; i < resultMessages.size(); i++) {
            messageList.add(resultMessages.get(i));
        }

        // sort
        Collections.sort(messageList, new Comparator<Message>() {
            @Override
            public int compare(Message lhs, Message rhs) {
                int id1 = Integer.parseInt(lhs.getId());
                int id2 = Integer.parseInt(rhs.getId());

                return Integer.compare(id1, id2);
            }
        });

        adapter = new MessageAdapter(messageList, userId);
        recyclerView.setAdapter(adapter);
        recyclerView.smoothScrollToPosition(adapter.getItemCount());
    }


    private void updateAdapter(RealmResults<Message> element) {
        for (int i = resultMessages.size() - 1; i < element.size(); i++) {
            adapter.addItem(resultMessages.get(i));
        }

        recyclerView.smoothScrollToPosition(adapter.getItemCount());
    }


    private void sendMenssage() {
        String message = editTextMessage.getText().toString();
        editTextMessage.setText("");

        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Constants.SERVER_URL);
        stringBuilder.append(Constants.MENSAGEM_PATH);

        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("origem_id", userId);
            jsonObject.put("destino_id", contactId);
            jsonObject.put("assunto", "");
            jsonObject.put("corpo", message);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.POST, stringBuilder.toString(), jsonObject, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject json) {
                        Message message = new Gson().fromJson(json.toString(), Message.class);
                        saveMessage(message);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onBackPressed();
                    }
                });

        requestQueue.add(request);
    }

    private void fetchUserSentMessages() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(Constants.SERVER_URL);
        stringBuilder.append(Constants.MENSAGEM_PATH);
        stringBuilder.append("/0/");
        stringBuilder.append(userId);
        stringBuilder.append("/");
        stringBuilder.append(contactId);

        JsonObjectRequest request = new JsonObjectRequest
                (Request.Method.GET, stringBuilder.toString(), null, new Response.Listener<JSONObject>() {
                    @Override
                    public void onResponse(JSONObject json) {
                        parseMessageList(json);
                    }
                }, new Response.ErrorListener() {
                    @Override
                    public void onErrorResponse(VolleyError error) {
                        onBackPressed();
                    }
                });

        requestQueue.add(request);
    }

    private void parseMessageList(JSONObject jsonRoot) {
        List<Message> messageList = new ArrayList<>();

        try {
            JSONArray jsonArray = jsonRoot.getJSONArray("mensagens");
            Gson gson = new Gson();

            for (int i = 0; i < jsonArray.length(); i++) {
                Message message = gson.fromJson(jsonArray.getJSONObject(i).toString(), Message.class);
                messageList.add(message);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        saveMessages(messageList);
    }

    private void saveMessage(final Message message) {
        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm bgRealm) {
                bgRealm.copyToRealmOrUpdate(message);
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
            }
        }, new Realm.Transaction.OnError() {
            @Override
            public void onError(Throwable error) {
            }
        });
    }

    private void saveMessages(final List<Message> messageList) {
        realm.executeTransactionAsync(new Realm.Transaction() {
            @Override
            public void execute(Realm bgRealm) {
                bgRealm.copyToRealmOrUpdate(messageList);
            }
        }, new Realm.Transaction.OnSuccess() {
            @Override
            public void onSuccess() {
                setupQuery();
                saveMessagesSynchronizedFlag();
            }
        }, new Realm.Transaction.OnError() {
            @Override
            public void onError(Throwable error) {
            }
        });
    }

    private void saveMessagesSynchronizedFlag() {
        Helpers.saveCurrentUserSentMessagesToContact(this, contactId);
    }
}