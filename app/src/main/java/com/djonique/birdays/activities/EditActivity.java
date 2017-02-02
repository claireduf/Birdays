package com.djonique.birdays.activities;

import android.Manifest;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.TextInputLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatCheckBox;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.djonique.birdays.R;
import com.djonique.birdays.database.DBHelper;
import com.djonique.birdays.models.Person;
import com.djonique.birdays.utils.ConstantManager;
import com.djonique.birdays.utils.ContactsInfo;
import com.djonique.birdays.utils.Utils;
import com.wdullaer.materialdatetimepicker.date.DatePickerDialog;

import java.util.Calendar;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.OnTextChanged;

public class EditActivity extends AppCompatActivity implements DatePickerDialog.OnDateSetListener {

    @BindView(R.id.tilEditName)
    TextInputLayout tilEditName;
    @BindView(R.id.etEditName)
    EditText etName;
    @BindView(R.id.tilEditDate)
    TextInputLayout tilEditDate;
    @BindView(R.id.etEditDate)
    EditText etDate;
    @BindView(R.id.cbEdit)
    AppCompatCheckBox checkBox;
    @BindView(R.id.etEditPhone)
    EditText etPhone;
    @BindView(R.id.etEditEmail)
    EditText etEmail;
    @BindView(R.id.fab_edit)
    FloatingActionButton fab;

    private Calendar calendar;
    private DBHelper dbHelper;
    private Person person;
    private boolean unknownYear;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit);

        ButterKnife.bind(this);

        dbHelper = new DBHelper(this);
        calendar = Calendar.getInstance();

        Intent intent = getIntent();
        long timeStamp = intent.getLongExtra(ConstantManager.TIME_STAMP, 0);
        person = dbHelper.query().getPerson(timeStamp);
        unknownYear = person.isYearUnknown();

        updateUI();

        calendar.setTimeInMillis(person.getDate());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_edit, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
            case android.R.id.home:
                this.onBackPressed();
                break;
            case R.id.menu_edit_ok:
                updatePerson();
                Intent update = new Intent();
                setResult(RESULT_OK, update);
                finish();
                this.overridePendingTransition(R.anim.edit_detail_in, R.anim.edit_detail_out);
        }
        return true;
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        overridePendingTransition(R.anim.edit_detail_in, R.anim.edit_detail_out);
    }

    private void updateUI() {
        etName.setText(person.getName());
        etName.setSelection(etName.getText().length());

        if (unknownYear) {
            etDate.setText(Utils.getUnknownDate(person.getDate()));
        } else {
            etDate.setText(Utils.getDate(person.getDate()));
        }

        checkBox.setChecked(unknownYear);
        etPhone.setText(person.getPhoneNumber());
        etEmail.setText(person.getEmail());
    }

    private void updatePerson() {
        person.setName(updateText(etName));
        person.setDate(calendar.getTimeInMillis());
        person.setYearUnknown(checkBox.isChecked());
        person.setPhoneNumber(updateText(etPhone));
        person.setEmail(updateText(etEmail));
        dbHelper.updateRec(person);
    }

    private String updateText(EditText editText) {
        String result = null;
        if (editText != null && editText.length() != 0) {
            result = editText.getText().toString();
        }
        return result;
    }

    @OnClick(R.id.etEditDate)
    void pickDate() {
        com.wdullaer.materialdatetimepicker.date.DatePickerDialog dpd =
                com.wdullaer.materialdatetimepicker.date.DatePickerDialog.newInstance(
                        EditActivity.this,
                        calendar.get(Calendar.YEAR),
                        calendar.get(Calendar.MONTH),
                        calendar.get(Calendar.DAY_OF_MONTH)
                );
        dpd.show(getFragmentManager(), ConstantManager.DATE_PICKER_FRAGMENT_TAG);
    }

    @Override
    public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
        calendar.set(Calendar.YEAR, year);
        calendar.set(Calendar.MONTH, monthOfYear);
        calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth);
        etDate.setText(Utils.getDate(calendar.getTimeInMillis()));
    }

    @OnTextChanged(value = R.id.etEditName, callback = OnTextChanged.Callback.TEXT_CHANGED)
    void validate() {
        if (etName.length() == 0) {
            fab.hide();
            tilEditName.setError(getString(R.string.error_hint));
        } else {
            if (!Utils.isEmptyDate(etDate) && Utils.isRightDate(calendar)) {
                fab.show();
            }
            tilEditName.setErrorEnabled(false);
        }
    }

    @OnTextChanged(value = R.id.etEditDate, callback = OnTextChanged.Callback.TEXT_CHANGED)
    void updateDate() {
        if (!Utils.isEmptyDate(etDate) && Utils.isRightDate(calendar)) {
            fab.show();
            tilEditDate.setErrorEnabled(false);
        } else if (Utils.isEmptyDate(etDate)) {
            fab.hide();
            tilEditDate.setError(getString(R.string.wrong_date));
        } else if (!Utils.isRightDate(calendar)) {
            fab.hide();
            tilEditDate.setError(getString(R.string.not_vanga));
        }
    }

    @OnClick(R.id.fab_edit)
    void addInfo() {
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.READ_CONTACTS) ==
                PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Intent.ACTION_PICK,
                    ContactsContract.Contacts.CONTENT_URI);
            startActivityForResult(intent, ConstantManager.REQUEST_READ_CONTACTS);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_CONTACTS},
                    ConstantManager.CONTACTS_REQUEST_PERMISSION_CODE);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            Uri contactData = data.getData();
            ContentResolver contentResolver = this.getContentResolver();
            Cursor cursor = contentResolver.query(contactData, null, null, null, null);
            if (cursor != null && cursor.getCount() > 0) {
                if (cursor.moveToFirst()) {
                    String id = cursor.getString(
                            cursor.getColumnIndex(ContactsContract.Contacts._ID));
                    etPhone.setText(ContactsInfo.retrievePhoneNumber(contentResolver, cursor, id));
                    etEmail.setText(ContactsInfo.retrieveEmail(contentResolver, id));
                }
            }
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}