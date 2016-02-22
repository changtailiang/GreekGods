/* Greek Gods: an Android application which shows the family tree of the Greek Gods.
 * Copyright (C) 2016 Tein van der Lugt
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.teinvdlugt.android.greekgods;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.CursorIndexOutOfBoundsException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.teinvdlugt.android.greekgods.models.Person;
import com.teinvdlugt.android.greekgods.models.Relation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PersonActivity extends AppCompatActivity {
    public static final String PERSON_ID_EXTRA = "person_id";

    private TextView parentsTextView, relationsTextView;
    private TextView descriptionTV, shortDescriptionTV;
    private int personId;

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_person);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        personId = getIntent().getIntExtra(PERSON_ID_EXTRA, -1);
        parentsTextView = (TextView) findViewById(R.id.parents_textView);
        relationsTextView = (TextView) findViewById(R.id.relations_textView);
        descriptionTV = (TextView) findViewById(R.id.description_textView);
        shortDescriptionTV = (TextView) findViewById(R.id.shortDescription_textView);

        if (personId != -1) {
            refresh();
        } else {
            Snackbar.make(findViewById(R.id.coordinatorLayout),
                    getString(R.string.something_went_wrong), Snackbar.LENGTH_INDEFINITE).show();
        }
    }

    private void refresh() {
        new AsyncTask<Void, Void, Void>() {
            private String name;
            private String description, shortDescription;
            private Map<Relation, List<String>> parents;
            private Map<Person, List<Person>> relationsAndChildren = new HashMap<>();

            @SuppressLint("DefaultLocale")
            @Override
            protected Void doInBackground(Void... params) {
                SQLiteDatabase db = openOrCreateDatabase("data", 0, null);
                Cursor c = null;

                // Person's name and description
                try {
                    String[] nameColumns = {"name", "shortDescription", "description"};
                    String[] selectionArgs = {String.valueOf(personId)};
                    c = db.query("people", nameColumns, "personId=?", selectionArgs, null, null, null);
                    c.moveToFirst();
                    name = c.getString(c.getColumnIndex("name"));
                    description = c.getString(c.getColumnIndex("description"));
                    shortDescription = c.getString(c.getColumnIndex("shortDescription"));
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } finally {
                    if (c != null) c.close();
                }

                // Parents
                parents = new HashMap<>();
                Cursor c2 = null;
                try {
                    String parentRelationsQuery = String.format(DBUtils.PARENTS_RELATIONS_QUERY, personId);
                    c = db.rawQuery(parentRelationsQuery, null);
                    int idColumn = c.getColumnIndex("relationId");
                    c.moveToFirst();
                    do {
                        int relationId = c.getInt(idColumn);
                        Relation relation = new Relation(relationId);
                        List<String> parentNames = new ArrayList<>();
                        String relationNamesQuery = String.format(DBUtils.NAMES_OF_RELATION_QUERY, relationId);
                        c2 = db.rawQuery(relationNamesQuery, null);
                        int nameColumn = c2.getColumnIndex("name");
                        c2.moveToFirst();
                        do {
                            parentNames.add(c2.getString(nameColumn));
                        } while (c2.moveToNext());
                        c2.close();
                        parents.put(relation, parentNames);
                    } while (c.moveToNext());
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } catch (CursorIndexOutOfBoundsException ignored) {
                } finally {
                    if (c != null) c.close();
                    if (c2 != null) c2.close();
                }

                // Relations
                Map<String, Integer> relations = new HashMap<>();
                try {
                    String relationsQuery = String.format(DBUtils.RELATIONS_OF_PERSON_QUERY, personId);
                    c = db.rawQuery(relationsQuery, null);
                    int nameColumn = c.getColumnIndex("name");
                    int relationIdColumn = c.getColumnIndex("relatiod_id");
                    c.moveToFirst();
                    do {
                        relations.put(c.getString(nameColumn), c.getInt(relationIdColumn));
                    } while (c.moveToNext());
                } catch (SQLiteException e) {
                    e.printStackTrace();
                } catch (CursorIndexOutOfBoundsException ignored) {
                } finally {
                    if (c != null) c.close();
                }

                // Children
                for (String partnerName : relations.keySet()) {
                    List<Person> children = new ArrayList<>();
                    try {
                        String birthsQuery = String.format(DBUtils.BIRTHS_QUERY, relations.get(partnerName));
                        c = db.rawQuery(birthsQuery, null);
                        int nameColumn = c.getColumnIndex("name");
                        int idColumn = c.getColumnIndex("personId");
                        c.moveToFirst();
                        do {
                            Person child = new Person();
                            child.setName(c.getString(nameColumn));
                            child.setId(c.getInt(idColumn));
                            children.add(child);
                        } while (c.moveToNext());
                    } catch (SQLiteException e) {
                        e.printStackTrace();
                    } catch (CursorIndexOutOfBoundsException ignored) {
                    } finally {
                        if (c != null) c.close();
                    }

                    Person partner = new Person();
                    partner.setName(partnerName);
                    relationsAndChildren.put(partner, children);
                }

                db.close();
                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                if (name != null) {
                    setTitle(name);
                }
                if (shortDescription != null) {
                    shortDescriptionTV.setText(shortDescription);
                } else {
                    shortDescriptionTV.setVisibility(View.GONE);
                }
                if (description != null) {
                    descriptionTV.setText(description);
                } else {
                    descriptionTV.setVisibility(View.GONE);
                }
                if (parents == null || parents.isEmpty()) {
                    parentsTextView.setText(R.string.no_parents);
                } else {
                    setParentTexts();
                }
                if (relationsAndChildren == null || relationsAndChildren.isEmpty()) {
                    relationsTextView.setText(R.string.no_relations);
                } else {
                    setRelationsAndChildrenTexts();
                }
            }

            private void setParentTexts() {
                SpannableStringBuilder ssb = new SpannableStringBuilder();
                for (final Relation relation : parents.keySet()) {
                    ClickableSpan cs = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            Toast.makeText(PersonActivity.this, "You clicked on the relation with id " +
                                    relation.getId(), Toast.LENGTH_SHORT).show();
                        }
                    };
                    List<String> parentNames = parents.get(relation);
                    StringBuilder relationText = new StringBuilder();
                    for (int i = 0; i < parentNames.size(); i++) {
                        if (i != 0) relationText.append(" & ");
                        relationText.append(parentNames.get(i));
                    }
                    ssb.append(relationText);
                    ssb.setSpan(cs, ssb.length() - relationText.length(),
                            ssb.length(), Spanned.SPAN_INCLUSIVE_INCLUSIVE);
                    ssb.append("\n");
                }
                ssb.delete(ssb.length() - 1, ssb.length());
                parentsTextView.setText(ssb);
                parentsTextView.setMovementMethod(LinkMovementMethod.getInstance());
            }

            private void setRelationsAndChildrenTexts() {
                SpannableStringBuilder ssb = new SpannableStringBuilder();
                for (final Person partner : relationsAndChildren.keySet()) {
                    ClickableSpan cs = new ClickableSpan() {
                        @Override
                        public void onClick(View widget) {
                            Toast.makeText(PersonActivity.this, "You clicked on the relation with " +
                                    partner.getName(), Toast.LENGTH_SHORT).show();
                        }
                    };
                    String text;
                    if (partner.getId() == personId) {
                        text = getString(R.string.single_relation_text, name);
                    } else {
                        text = name + " & " + partner.getName();
                    }
                    ssb.append(text).append("\n");
                    ssb.setSpan(cs, ssb.length() - text.length() - 1,
                            ssb.length() - 1, Spanned.SPAN_INCLUSIVE_INCLUSIVE);

                    for (final Person child : relationsAndChildren.get(partner)) {
                        ClickableSpan cs2 = new ClickableSpan() {
                            @Override
                            public void onClick(View widget) {
                                openActivity(PersonActivity.this, child.getId());
                            }
                        };
                        ssb.append("\t\t").append(child.getName()).append("\n");
                        ssb.setSpan(cs2, ssb.length() - child.getName().length() - 1,
                                ssb.length() - 1, Spanned.SPAN_INCLUSIVE_EXCLUSIVE);
                    }
                }
                ssb.delete(ssb.length() - 1, ssb.length());
                relationsTextView.setText(ssb);
                relationsTextView.setMovementMethod(LinkMovementMethod.getInstance());
            }
        }.execute();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return false;
    }

    public static void openActivity(Context context, int personId) {
        Intent intent = new Intent(context, PersonActivity.class);
        intent.putExtra(PERSON_ID_EXTRA, personId);
        context.startActivity(intent);
    }
}
