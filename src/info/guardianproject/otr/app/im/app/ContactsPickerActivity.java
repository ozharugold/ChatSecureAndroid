/*
 * Copyright (C) 2007-2008 Esmertec AG. Copyright (C) 2007-2008 The Android Open
 * Source Project
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package info.guardianproject.otr.app.im.app;

import info.guardianproject.otr.app.im.R;
import info.guardianproject.otr.app.im.provider.Imps;
import android.app.SearchManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.LoaderManager;
import android.support.v4.widget.ResourceCursorAdapter;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.actionbarsherlock.app.SherlockListActivity;
import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuInflater;
import com.actionbarsherlock.view.MenuItem;
import com.actionbarsherlock.widget.SearchView;

/** Activity used to pick a contact. */
public class ContactsPickerActivity extends SherlockListActivity  {
    public final static String EXTRA_EXCLUDED_CONTACTS = "excludes";

    public final static String EXTRA_RESULT_USERNAME = "result";
    public final static String EXTRA_RESULT_PROVIDER = "provider";
    public final static String EXTRA_RESULT_ACCOUNT = "account";    

    private ContactAdapter mAdapter;
    private String mExcludeClause;
    Uri mData;
    
    private String mSearchString;

    SearchView mSearchView = null;


    // The loader's unique id. Loader ids are specific to the Activity or
    // Fragment in which they reside.
    private static final int LOADER_ID = 1;

    // The callbacks through which we will interact with the LoaderManager.
    private LoaderManager.LoaderCallbacks<Cursor> mCallbacks;
    
    private boolean mHideOffline = false;
    
    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        ((ImApp)getApplication()).setAppTheme(this);

        setContentView(R.layout.contacts_picker_activity);

        mAdapter = new ContactAdapter(ContactsPickerActivity.this, R.layout.contact_view);
        setListAdapter(mAdapter);
       
        ContentResolver cr = getContentResolver();
        Cursor pCursor = cr.query(Imps.ProviderSettings.CONTENT_URI,new String[] {Imps.ProviderSettings.NAME, Imps.ProviderSettings.VALUE},Imps.ProviderSettings.PROVIDER + "=?",new String[] { Long.toString(Imps.ProviderSettings.PROVIDER_ID_FOR_GLOBAL_SETTINGS)},null);
        Imps.ProviderSettings.QueryMap globalSettings = new Imps.ProviderSettings.QueryMap(pCursor, cr, Imps.ProviderSettings.PROVIDER_ID_FOR_GLOBAL_SETTINGS, true, null);
        mHideOffline = globalSettings.getHideOfflineContacts();
        
        globalSettings.close();
       
        doFilterAsync("");
    }
    
    

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getSupportMenuInflater();
        inflater.inflate(R.menu.contact_list_menu, menu);
        
        SearchManager searchManager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
        mSearchView = (SearchView) menu.findItem(R.id.menu_search).getActionView();
        if (mSearchView != null )
        {
            mSearchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
            mSearchView.setIconifiedByDefault(false);   
        }

        SearchView.OnQueryTextListener queryTextListener = new SearchView.OnQueryTextListener() 
        {
            public boolean onQueryTextChange(String newText) 
            {
                mSearchString = newText;
                doFilterAsync(mSearchString);
                return true;
            }

            public boolean onQueryTextSubmit(String query) 
            {
                mSearchString = query;
                doFilterAsync(mSearchString);
                
                return true;
            }
        };
        
        mSearchView.setOnQueryTextListener(queryTextListener);
        
       

        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {

        case R.id.menu_invite_user:
            Intent i = new Intent(ContactsPickerActivity.this, AddContactActivity.class);
         
            startActivity(i);
            return true;

       
        }
        return super.onOptionsItemSelected(item);
    }


    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        Cursor cursor = (Cursor) mAdapter.getItem(position);
        Intent data = new Intent();
        data.putExtra(EXTRA_RESULT_USERNAME, cursor.getString(ContactView.COLUMN_CONTACT_USERNAME));
        data.putExtra(EXTRA_RESULT_PROVIDER, cursor.getLong(ContactView.COLUMN_CONTACT_PROVIDER));
        data.putExtra(EXTRA_RESULT_ACCOUNT, cursor.getLong(ContactView.COLUMN_CONTACT_ACCOUNT));
        
        setResult(RESULT_OK, data);
        finish();
    }
    
    public void doFilterAsync (final String query)
    {
        new Thread(new Runnable () { public void run () {
            doFilter(query);
        }}).start();
    }
    
    public void doFilter(String filterString) {
        mSearchString = filterString;
        
        StringBuilder buf = new StringBuilder();

        if (mSearchString != null) {
            
            buf.append('(');
            buf.append(Imps.Contacts.NICKNAME);
            buf.append(" LIKE ");
            DatabaseUtils.appendValueToSql(buf, "%" + mSearchString + "%");
            buf.append(" OR ");
            buf.append(Imps.Contacts.USERNAME);
            buf.append(" LIKE ");
            DatabaseUtils.appendValueToSql(buf, "%" + mSearchString + "%");
            buf.append(')');
            buf.append(" AND ");
        }
        
        //normal types not temporary
        buf.append(Imps.Contacts.TYPE).append('=').append(Imps.Contacts.TYPE_NORMAL);
        
       
        if(mHideOffline)
        {
            buf.append(" AND ");
            buf.append(Imps.Contacts.PRESENCE_STATUS).append("!=").append(Imps.Presence.OFFLINE);
           
        }
        
        
        mCursor = getContentResolver().query(Imps.Contacts.CONTENT_URI_CONTACTS_BY, ContactView.CONTACT_PROJECTION,
                    buf == null ? null : buf.toString(), null, Imps.Contacts.ALPHA_SORT_ORDER);
        
        mHandlerCursorUpdater.sendEmptyMessage(0);
        
    }
    
    private Cursor mCursor;
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        mAdapter.getCursor().close();
    }

    private class ContactAdapter extends ResourceCursorAdapter {
        
        
        public ContactAdapter(Context context, int view) {
            super(context, view, null);
            
        }

        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            
            View view = super.newView(context, cursor, parent);
          
            ContactView.ViewHolder holder = null;
            
            holder = new ContactView.ViewHolder();
                
            holder.mLine1 = (TextView) view.findViewById(R.id.line1);
            holder.mLine2 = (TextView) view.findViewById(R.id.line2);
                           
            holder.mAvatar = (ImageView)view.findViewById(R.id.avatar);                
            holder.mStatusIcon = (ImageView)view.findViewById(R.id.statusIcon);
            
            holder.mContainer = view.findViewById(R.id.message_container);
                
            view.setTag(holder);
            
           return view;
           
        
               
        }
        

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            ContactView v = (ContactView) view;
            v.bind(cursor, mSearchString, true);
            
        }
    }
    

    private Handler mHandlerCursorUpdater = new Handler ()
    {
        @Override
        public void handleMessage(Message msg) {
            mAdapter.changeCursor(mCursor);

        }    
    };
   
}
