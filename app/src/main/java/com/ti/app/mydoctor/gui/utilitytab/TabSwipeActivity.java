package com.ti.app.mydoctor.gui.utilitytab;

import java.util.ArrayList;
import java.util.List;
 
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBar.Tab;
import android.support.v7.app.ActionBar.TabListener;
import android.support.v7.app.ActionBarActivity;
import android.view.LayoutInflater;
import android.view.View;
import com.ti.app.mydoctor.R;
import com.ti.app.mydoctor.gui.customview.GWTextView;

public abstract class TabSwipeActivity extends ActionBarActivity {
	private ViewPager mViewPager;
    private TabsAdapter adapter;
         
    @Override
    public void onCreate(Bundle savedInstanceState) {
        /*
         * Create the ViewPager and our custom adapter
         */
        mViewPager = new ViewPager(this);
        adapter = new TabsAdapter( this, mViewPager );
        mViewPager.setAdapter( adapter );
        mViewPager.setOnPageChangeListener( adapter );
 
        /*
         * We need to provide an ID for the ViewPager, otherwise we will get an exception like:
         *
         * java.lang.IllegalArgumentException: No view found for id 0xffffffff for fragment TestFragment{40de5b90 #0 id=0xffffffff android:switcher:-1:0}
         * at android.support.v4.app.FragmentManagerImpl.moveToState(FragmentManager.java:864)
         *
         * The ID 0x7F04FFF0 is large enough to probably never be used for anything else
         */
        //mViewPager.setId( 0x7F04FFF0 );
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            mViewPager.setId( View.generateViewId() );
        } else {
            mViewPager.setId( R.id.tab_swipe_pager );
        }

        super.onCreate(savedInstanceState);
 
        /*
         * Set the ViewPager as the content view
         */
        setContentView(mViewPager);        
    }
    
    /**
     * Add a tab with a backing Fragment to the action bar
     * @param titleRes A string resource pointing to the title for the tab
     * @param fragmentClass The class of the Fragment to instantiate for this tab
     * @param args An optional Bundle to pass along to the Fragment (may be null)
     */
    protected void addTab(int titleRes, Class fragmentClass, Bundle args, boolean selected ) {
        adapter.addTab( getString( titleRes ), fragmentClass, args, selected );
    }
    /**
     * Add a tab with a backing Fragment to the action bar
     * @param title A string to be used as the title for the tab
     * @param fragmentClass The class of the Fragment to instantiate for this tab
     * @param args An optional Bundle to pass along to the Fragment (may be null)
     */
    protected void addTab(CharSequence title, Class fragmentClass, Bundle args, boolean selected ) {
        adapter.addTab( title, fragmentClass, args, selected );
    }
    
    protected void setPageChangedListener(OnPageChangedListener listener){
    	adapter.setPageChangedListener(listener);
    }
    
    private static class TabsAdapter extends FragmentPagerAdapter implements TabListener, ViewPager.OnPageChangeListener {
 
        private final ActionBarActivity mActivity;
        private final ActionBar mActionBar;
        private GWTextView titleTV;
        private final ViewPager mPager;
        private OnPageChangedListener mListener;
 
        /**
         * @param activity
         * @param pager
         */
        public TabsAdapter(ActionBarActivity activity, ViewPager pager) {
            super(activity.getSupportFragmentManager());
            this.mActivity = activity;
            this.mActionBar = activity.getSupportActionBar();
            this.mPager = pager;
            
            //mPager.setAdapter(this);
            //mPager.setOnPageChangeListener(this);
 
            
            mActionBar.setNavigationMode( ActionBar.NAVIGATION_MODE_TABS );
            
            //Setta il gradiente di sfondo della action bar
    		Drawable cd = mActivity.getResources().getDrawable(R.drawable.action_bar_background_color);
    		mActionBar.setBackgroundDrawable(cd);
    		
    		mActionBar.setDisplayShowCustomEnabled(true);
    		mActionBar.setDisplayShowTitleEnabled(false);
    		
    		//Setta l'icon
    		mActionBar.setIcon(R.drawable.icon_action_bar);

    		//Settare il font e il titolo della Activity
    		LayoutInflater inflator = (LayoutInflater)mActivity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    		View titleView = inflator.inflate(R.layout.actionbar_title, null);
    		titleTV = (GWTextView)titleView.findViewById(R.id.actionbar_title_label);
    		titleTV.setText(mActivity.getResources().getString(R.string.connectionSettings));
    		mActionBar.setCustomView(titleView);
    				
    		//L'icona dell'App diventa tasto per tornare nella Home
    		mActionBar.setHomeButtonEnabled(true);
    		mActionBar.setDisplayHomeAsUpEnabled(true);    		
        }
 
        private static class TabInfo {
            public final Class fragmentClass;
            public final Bundle args;
            public TabInfo(Class fragmentClass,
                    Bundle args) {
                this.fragmentClass = fragmentClass;
                this.args = args;
            }
        }
 
        private List mTabs = new ArrayList();
 
        public void addTab( CharSequence title, Class fragmentClass, Bundle args, boolean selected ) {
            final TabInfo tabInfo = new TabInfo( fragmentClass, args );
 
            Tab tab = mActionBar.newTab();
            tab.setText( title );
            tab.setTabListener( this );
            tab.setTag( tabInfo );
 
            mTabs.add( tabInfo );
 
            mActionBar.addTab( tab, selected );
            notifyDataSetChanged();
        }
        
        public void setPageChangedListener(OnPageChangedListener listener) {
        	this.mListener = listener;
        }
 
        @Override
        public Fragment getItem(int position) {
            final TabInfo tabInfo = (TabInfo) mTabs.get( position );
            return Fragment.instantiate( mActivity, tabInfo.fragmentClass.getName(), tabInfo.args );
        }
 
        @Override
        public int getCount() {
            return mTabs.size();
        }
 
        public void onPageScrollStateChanged(int arg0) {
        }
 
        public void onPageScrolled(int arg0, float arg1, int arg2) {
        }
 
        public void onPageSelected(int position) {
        	mActionBar.setSelectedNavigationItem( position );     
        	if(mListener!=null)
        		mListener.onPageChanged();
        }
 
        public void onTabSelected(Tab tab, FragmentTransaction ft) {
            TabInfo tabInfo = (TabInfo) tab.getTag();
            for ( int i = 0; i < mTabs.size(); i++ ) {
                if ( mTabs.get( i ) == tabInfo ) {
                    mPager.setCurrentItem( i );
                }
            }
        }
 
        public void onTabUnselected(Tab tab, FragmentTransaction ft) {
        }
 
        public void onTabReselected(Tab tab, FragmentTransaction ft) {
        }       
        
    }    
}
