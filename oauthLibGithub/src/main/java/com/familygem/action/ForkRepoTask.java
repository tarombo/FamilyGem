package com.familygem.action;

import static android.content.Context.MODE_PRIVATE;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;

import androidx.core.util.Consumer;

import com.familygem.oauthLibGithub.BuildConfig;
import com.familygem.restapi.APIInterface;
import com.familygem.restapi.ApiClient;
import com.familygem.restapi.models.Commit;
import com.familygem.restapi.models.Content;
import com.familygem.restapi.models.Repo;
import com.familygem.restapi.models.TreeResult;
import com.familygem.restapi.models.User;
import com.familygem.utility.FamilyGemTreeInfoModel;
import com.familygem.utility.Helper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

public class ForkRepoTask {
    private static final String TAG = "ForkRepoTask";
    public static void execute(Context context, String repoFullName, int nextTreeId,
                               Runnable beforeExecution, Consumer<FamilyGemTreeInfoModel> afterExecution,
                               Consumer<String> errorExecution) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            // background thread
            try {
                handler.post(beforeExecution);
                // prepare api
                SharedPreferences prefs = context.getSharedPreferences("github_prefs", MODE_PRIVATE);
		        String oauthToken = prefs.getString("oauth_token", null);
                APIInterface apiInterface = ApiClient.getClient(BuildConfig.GITHUB_BASE_URL, oauthToken).create(APIInterface.class);

                // get username API /user
                File userFile = new File(context.getFilesDir(), "user.json");
                User user = Helper.getUser(userFile);

                // check if the repo belongs to himself
                String[] repoNameSegments = repoFullName.split("/");
                Log.d(TAG, "owner:" + repoNameSegments[0] + " repo:" + repoNameSegments[1]);
                assert user != null;
                if (repoNameSegments[0].equals(user.login)) {
                    handler.post(() -> errorExecution.accept("E001"));
                    return;
                }

                // call api fork repo
                Call<Repo> repoCall = apiInterface.forkUserRepo(repoNameSegments[0], repoNameSegments[1]);
                Response<Repo> repoResponse = repoCall.execute();
                Log.d(TAG, "repo response code:" + repoResponse.code());
                Repo repo = repoResponse.body();
                if (repo == null) {
                    handler.post(() -> errorExecution.accept("E404"));
                    return;
                }
                Log.d(TAG, "repo full_name:" + repo.fullName);

                // save repo object to local json file [treeId].repo
                Gson gson = new GsonBuilder().setPrettyPrinting().create();
                String jsonRepo = gson.toJson(repo);
                FileUtils.writeStringToFile(new File(context.getFilesDir(), nextTreeId + ".repo"), jsonRepo, "UTF-8");

                Content treeJsonContent = null;

                int i = 0;
                while (true){
                    try {
                        // give time for the github server to process the fork request
                        Thread.sleep(2000);

                        // download file tree.json
                        treeJsonContent = DownloadFileHelper.downloadFile(apiInterface,user.login, repoNameSegments[1], "tree.json");
                        break;
                    }
                    catch (IOException ex){
                        i++;
                        if(Objects.equals(ex.getMessage(), Helper.ERROR_RATE_LIMIT) || i == 3){
                            throw ex;
                        }
                    }
                }

                // save tree.json to local directory
                File treeJsonFile = new File(context.getFilesDir(), nextTreeId + ".json");
                FileUtils.writeStringToFile(treeJsonFile, treeJsonContent.contentStr, "UTF-8");
                File treeJsonFileHead0 = new File(context.getFilesDir(), nextTreeId + ".head_0");
                File treeJsonFileBehind0 = new File(context.getFilesDir(), nextTreeId + ".behind_0");
                FileUtils.copyFile(treeJsonFile, treeJsonFileHead0);
                FileUtils.copyFile(treeJsonFile, treeJsonFileBehind0);

                // download file info.json
                Content infoJsonContent = DownloadFileHelper.downloadFile(apiInterface, user.login, repoNameSegments[1], "info.json");
                // create treeInfoModel instance
                FamilyGemTreeInfoModel treeInfoModel = gson.fromJson(infoJsonContent.contentStr, FamilyGemTreeInfoModel.class);

                // download all media files
                TreeResult baseTree = Helper.getBaseTreeCall(apiInterface, repoNameSegments[0], repoNameSegments[1]);
                File dirMedia = Helper.getDirMedia(context, nextTreeId);
                Helper.downloadAllMediaFiles(context, dirMedia, baseTree, apiInterface, repoNameSegments[0], repoNameSegments[1]);

                // get last commit
                Call<List<Commit>> commitsCall = apiInterface.getLatestCommit(user.login, repoNameSegments[1]);
                Response<List<Commit>> commitsResponse = commitsCall.execute();
                List<Commit> commits = commitsResponse.body();
                String commitStr = gson.toJson(commits.get(0));
                FileUtils.writeStringToFile(new File(context.getFilesDir(), nextTreeId + ".commit"), commitStr, "UTF-8");

                //UI Thread work here
                treeInfoModel.githubRepoFullName = repo.fullName;
                treeInfoModel.filePath = treeJsonFile.getAbsolutePath();
                treeInfoModel.repoStatus = "identical";
                treeInfoModel.aheadBy = 0;
                treeInfoModel.behindBy = 0;
                treeInfoModel.totalCommits = 0;
                handler.post(() -> afterExecution.accept(treeInfoModel));
            } catch (Throwable ex) {
                Log.e(TAG, "ForkRepoTask is failed", ex);
                handler.post(() -> errorExecution.accept(ex.getLocalizedMessage()));
            }
        });
    }
}
