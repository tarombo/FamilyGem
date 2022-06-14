package app.familygem;

import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleAdapter;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;

import com.familygem.action.GetMyReposTask;
import com.familygem.restapi.models.Repo;
import com.familygem.utility.FamilyGemTreeInfoModel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RecoverTreesActivity extends AppCompatActivity {
    ListView listView;
    List<Map<String, String>> pullList;
    SimpleAdapter adapter;
    ProgressBar pc;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recover_trees);
        listView = findViewById(R.id.list);
        pc = findViewById(R.id.progress_circular);
        getData();
    }

    private List<String> getListOfCurrentRepoFullNames() {
        List<String> repoFullNames = new ArrayList<>();
        for (Settings.Tree tree : Global.settings.trees) {
            if (tree.githubRepoFullName != null)
                repoFullNames.add(tree.githubRepoFullName);
        }
        return repoFullNames;
    }

    private void getData() {
        pc.setVisibility(View.VISIBLE);
        List<String> repoFullNames = getListOfCurrentRepoFullNames();
        GetMyReposTask.execute(RecoverTreesActivity.this, repoFullNames,  treeInfos -> {
            pullList = new ArrayList<>();
            for (FamilyGemTreeInfoModel treeInfo : treeInfos ) {
                Settings.Tree tree = new Settings.Tree(-1,
                        treeInfo.title,
                        treeInfo.filePath,
                        treeInfo.persons,
                        treeInfo.generations,
                        treeInfo.root,
                        null,
                        treeInfo.grade,
                        treeInfo.githubRepoFullName
                );
                Map<String, String> dato = new HashMap<>(3);
                dato.put("repoFullName", treeInfo.githubRepoFullName);
                dato.put("dati", Alberi.scriviDati(RecoverTreesActivity.this, tree));
                dato.put("titolo", treeInfo.title);
                pullList.add(dato);
            }
            adapter = new SimpleAdapter(RecoverTreesActivity.this, this.pullList,
                    R.layout.pezzo_albero,
                    new String[] {"titolo", "dati"},
                    new int[] {R.id.albero_titolo, R.id.albero_dati})
            {
                @Override
                public View getView(final int posiz, View convertView, ViewGroup parent) {
                    View vistaAlbero = super.getView( posiz, convertView, parent );
                    final String repoFullName = pullList.get(posiz).get("repoFullName");
                    vistaAlbero.findViewById(R.id.albero_menu).setOnClickListener( vista -> {
                        PopupMenu popup = new PopupMenu(RecoverTreesActivity.this, vista);
                        Menu menu = popup.getMenu();
                        menu.add(0, 0, 0, R.string.recover_tree);
                        popup.show();
                        popup.setOnMenuItemClickListener(item -> {
                            int id = item.getItemId();
                            if (id == 0) {
                                recoverTree(repoFullName);
                            } else {
                                return false;
                            }
                            return true;
                        });
                    });
                    return vistaAlbero;
                }
            }
            ;
            listView.setAdapter(adapter);
            pc.setVisibility(View.GONE);
        }, error -> new AlertDialog.Builder(RecoverTreesActivity.this)
                .setTitle(R.string.find_errors)
                .setMessage(error)
                .setCancelable(false)
                .setPositiveButton(R.string.OK, (gDialog, gwhich) -> gDialog.dismiss())
                .show());
    }

    private void recoverTree(String repoFullName) {
        // TODO download repo
        Toast.makeText(RecoverTreesActivity.this, "DOWNLOAD REPO:" + repoFullName, Toast.LENGTH_LONG).show();
    }
}