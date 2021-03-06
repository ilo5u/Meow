package top.rechinx.meow.ui.home

import android.content.Intent
import com.google.android.material.navigation.NavigationView
import androidx.core.view.GravityCompat
import androidx.appcompat.app.ActionBarDrawerToggle
import android.view.Menu
import android.view.MenuItem
import com.miguelcatalan.materialsearchview.MaterialSearchView
import kotlinx.android.synthetic.main.activity_home.*
import kotlinx.android.synthetic.main.custom_toolbar.*
import top.rechinx.meow.R
import top.rechinx.meow.ui.about.AboutActivity
import top.rechinx.meow.ui.base.BaseActivity
import top.rechinx.meow.ui.grid.history.HistoryFragment
import top.rechinx.meow.ui.result.ResultActivity
import top.rechinx.meow.ui.setting.SettingsActivity
import top.rechinx.meow.ui.source.SourceFragment

class MainActivity: BaseActivity(), NavigationView.OnNavigationItemSelectedListener, MaterialSearchView.OnQueryTextListener {

    override fun initViews() {
        supportFragmentManager.beginTransaction().replace(R.id.containerFragment, HomeFragment()).commit()
        navigationView.setCheckedItem(R.id.drawer_main)
        initDrawer()
        searchView.setOnQueryTextListener(this)
    }

    private fun initDrawer() {
        val drawerToggle = ActionBarDrawerToggle(this, drawerLayout, customToolbar, 0, 0)
        drawerToggle.syncState()
        drawerLayout.addDrawerListener(drawerToggle)
        navigationView.setNavigationItemSelectedListener(this)
    }

    override fun getLayoutId(): Int = R.layout.activity_home

    override fun onNavigationItemSelected(item: MenuItem): Boolean {
        when(item.itemId) {
            R.id.drawer_main -> {
                supportFragmentManager.beginTransaction().replace(R.id.containerFragment, HomeFragment()).commit()
                customToolbar?.title = getString(R.string.app_name)
            }
            R.id.drawer_source -> {
                supportFragmentManager.beginTransaction().replace(R.id.containerFragment, SourceFragment()).commit()
                customToolbar?.title = item.title
            }
            R.id.drawer_history -> {
                supportFragmentManager.beginTransaction().replace(R.id.containerFragment, HistoryFragment()).commit()
                customToolbar?.title = item.title
            }
            R.id.drawer_about -> {
                startActivity(AboutActivity.createIntent(this))
            }
            R.id.drawer_settings -> {
                var intent = Intent(this, SettingsActivity::class.java)
                startActivity(intent)
            }
        }
        drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    override fun onQueryTextSubmit(query: String): Boolean {
        if (query.isEmpty()) {
            searchView.setHint(getString(R.string.empty_for_search))
        } else {
            startActivity(ResultActivity.createIntent(this@MainActivity, query))
        }
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        return false
    }


    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.bar_menu, menu);
        val item = menu?.findItem(R.id.action_search)
        searchView.setMenuItem(item)
        return true
    }

    override fun onBackPressed() {
        if (searchView.isSearchOpen) {
            searchView.closeSearch()
        } else {
            super.onBackPressed()
        }
    }
}