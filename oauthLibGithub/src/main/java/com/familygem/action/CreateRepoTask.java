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
import com.familygem.restapi.models.CreateBlobResult;
import com.familygem.restapi.models.FileContent;
import com.familygem.restapi.models.RefResult;
import com.familygem.restapi.models.Repo;
import com.familygem.restapi.models.TreeResult;
import com.familygem.restapi.models.TreeItem;
import com.familygem.restapi.models.User;
import com.familygem.restapi.requestmodels.CommitTreeRequest;
import com.familygem.restapi.requestmodels.CommitterRequestModel;
import com.familygem.restapi.requestmodels.CreateBlobRequestModel;
import com.familygem.restapi.requestmodels.CreateRepoRequestModel;
import com.familygem.restapi.requestmodels.CreateTreeRequestModel;
import com.familygem.restapi.requestmodels.FileRequestModel;
import com.familygem.restapi.requestmodels.UpdateRefRequestModel;
import com.familygem.utility.FamilyGemTreeInfoModel;
import com.familygem.utility.Helper;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.apache.commons.io.FileUtils;
import org.folg.gedcom.model.Gedcom;
import org.folg.gedcom.model.Media;
import org.folg.gedcom.model.Person;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import retrofit2.Call;
import retrofit2.Response;

public class CreateRepoTask {
    private static final String TAG = "CreateRepoTask";
    public static void execute(Context context, int treeId, final String email, FamilyGemTreeInfoModel treeInfoModel, Gedcom gedcom,
                               Helper.FWrapper fWrapper,
                               Runnable beforeExecution, Consumer<String> afterExecution,
                               Consumer<String> errorExecution) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Handler handler = new Handler(Looper.getMainLooper());
        executor.execute(() -> {
            // background thread
            try {
                handler.post(beforeExecution);
                Gson gson = new GsonBuilder().setPrettyPrinting().create();

                // prepare api
                SharedPreferences prefs = context.getSharedPreferences("github_prefs", MODE_PRIVATE);
		        String oauthToken = prefs.getString("oauth_token", null);
                APIInterface apiInterface = ApiClient.getClient(BuildConfig.GITHUB_BASE_URL, oauthToken).create(APIInterface.class);

                // get username API /user
                File userFile = new File(context.getFilesDir(), "user.json");
                User user = Helper.getUser(userFile);

                // generate repoName
                SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
                Date date = new Date();
                String repoName = "tarombo-" + user.login + "-" + formatter.format(date);

                // create folder and multiple files under single commit see https://dev.to/bro3886/create-a-folder-and-push-multiple-files-under-a-single-commit-through-github-api-23kc

                // (step 1: create repo) call api create repo and get response
                String description = "A family tree by Tarombo app - " + treeInfoModel.title + "";
                Call<Repo> repoCall = apiInterface.createUserRepo(new CreateRepoRequestModel(repoName, description));
                Response<Repo> repoResponse = repoCall.execute();
                Log.d(TAG, "repo response code:" + repoResponse.code());
                Repo repo = repoResponse.body();
                Log.d(TAG, "repo full_name:" + repo.fullName);
                treeInfoModel.githubRepoFullName = repo.fullName;

                // give time the github server to process
                Thread.sleep(2000);

                // (step 2: create first commit) which is README.md
                // update file README.md (also as first commit, we need at least one commit to work with tree database in github rest api)
                String readmeString = "# " + treeInfoModel.title + "\n" +
                        "A family tree by Tarombo app (https://tarombo.siboro.org/) \n" +
                        "Do not edit the files in this repository manually!  \n" +
                        "URL to share: " + Helper.generateDeepLink(repo.fullName);
                byte[] readmeStringBytes = readmeString.getBytes(StandardCharsets.UTF_8);
                String readmeBase64 = Base64.encodeToString(readmeStringBytes, Base64.DEFAULT);
                FileRequestModel createReadmeRequestModel = new FileRequestModel(
                        "initial commit",
                        readmeBase64,
                        new CommitterRequestModel(user.getUserName(), email)
                );
                Call<FileContent> createReadmeFileCall = apiInterface.createFile(user.login, repoName, "README.md", createReadmeRequestModel);
                Response<FileContent> createReadmeFileResponse = createReadmeFileCall.execute();
                FileContent createReadmeCommit = createReadmeFileResponse.body();
                // get last commit
                Commit lastCommit = createReadmeCommit.commit;

                // (step 3: get base_tree)
                TreeResult baseTree = Helper.getBaseTreeCall(apiInterface, user.login, repoName);

                // (step 4: create tree and its items -> file blob and subfolder)
                List<TreeItem> treeItemList = baseTree.tree; // initialize with original tree items (which is only README.md)
                // create tree item for tree.json
                File file = new File(context.getFilesDir(), treeId + ".json");
                TreeItem treeJsonFile = Helper.createItemBlob(apiInterface, user.login, repoName, file, "tree.json");
                if (treeJsonFile != null)
                    treeItemList.add(treeJsonFile);
                // create tree item for info.json
                TreeItem infoFile = Helper.createItemBlob(apiInterface, user.login, repoName,
                        gson.toJson(treeInfoModel).getBytes(StandardCharsets.UTF_8), "info.json");
                if (infoFile != null)
                    treeItemList.add(infoFile);
                // create blob and tree item for media file
                for (Person person: gedcom.getPeople()) {
                    for (Media media: person.getAllMedia(gedcom)) {
                        File fileMedia = fWrapper.getFileMedia(treeId, media);
                        if (fileMedia == null)
                            continue;
                        TreeItem mediaTreeItem = Helper.createItemBlob(apiInterface, user.login, repoName, media, fileMedia);
                        if (mediaTreeItem != null)
                            treeItemList.add(mediaTreeItem);
                    }
                }

                // create the tree
                CreateTreeRequestModel createTreeRequestModel = new CreateTreeRequestModel();
                createTreeRequestModel.baseTree = baseTree.sha;
                createTreeRequestModel.tree = treeItemList;
                Call<TreeResult> createTreeCall = apiInterface.createTree(user.login,repoName, createTreeRequestModel);
                Response<TreeResult> createTreeCallResponse = createTreeCall.execute();
                TreeResult treeResult = createTreeCallResponse.body();

                // (step 5: create commit of the new tree)
                CommitTreeRequest commitTreeRequest = new CommitTreeRequest();
                commitTreeRequest.message = "initial commit";
                commitTreeRequest.author = new CommitterRequestModel(user.getUserName(), email);
                commitTreeRequest.tree = treeResult.sha;
                commitTreeRequest.parents = Collections.singletonList(lastCommit.sha);
                Call<Commit> createTreeCommitCall = apiInterface.createCommitTree(user.login, repoName, commitTreeRequest);
                Response<Commit> createTreeCommitResponse = createTreeCommitCall.execute();
                Commit createTreeCommit = createTreeCommitResponse.body();

                // (step 6: updating ref)
                // Update the reference of your branch to point to the new commit SHA
                UpdateRefRequestModel refRequestModel = new UpdateRefRequestModel();
                refRequestModel.sha = createTreeCommit.sha;
                Call<RefResult> updateRefCall = apiInterface.updateRef(user.login, repoName, refRequestModel);
                Response<RefResult> updateRefResponse = updateRefCall.execute();
                RefResult refResult = updateRefResponse.body();

                // (step 7: save last commit)
                String commitStr = gson.toJson(createTreeCommit);
                FileUtils.writeStringToFile(new File(context.getFilesDir(), treeId + ".commit"), commitStr, "UTF-8");

                // generate deeplink
                final String deeplinkUrl = Helper.generateDeepLink(repo.fullName);

                // save repo object to local json file [treeId].repo
                String jsonRepo = gson.toJson(repo);
                FileUtils.writeStringToFile(new File(context.getFilesDir(), treeId + ".repo"), jsonRepo, "UTF-8");

                // create private repo
                String description2 = "A family tree by Tarombo app - " + treeInfoModel.title + " [private]";
                Call<Repo> repoCall2 = apiInterface.createUserRepo(new CreateRepoRequestModel(repoName + "-private", description2, true));
                Response<Repo> repoResponse2 = repoCall2.execute();
                Log.d(TAG, "repo response2 code:" + repoResponse2.code());
                // update file README.md
                String readmeString2 = "# " + treeInfoModel.title + " [private] \n" +
                        "A family tree by Tarombo app (https://tarombo.siboro.org/) \n" +
                        "This is the private part of the family tree \n" +
                        "Do not edit the files in this repository manually!";
                byte[] readmeStringBytes2 = readmeString2.getBytes(StandardCharsets.UTF_8);
                String readmeBase642 = Base64.encodeToString(readmeStringBytes2, Base64.DEFAULT);
                FileRequestModel createReadmeRequestModel2 = new FileRequestModel(
                        "initial commit",
                        readmeBase642,
                        new CommitterRequestModel(user.getUserName(), email)
                );
                Call<FileContent> createReadmeFileCall2 = apiInterface.createFile(user.login, repoName + "-private", "README.md", createReadmeRequestModel2);
                createReadmeFileCall2.execute();
                // upload tree-private.json (if any)
                File privateFile = new File(context.getFilesDir(), treeId + ".private.json");
                if (privateFile.exists()) {
                    int size = (int) privateFile.length();
                    byte[] bytes = new byte[size];
                    BufferedInputStream buf = new BufferedInputStream(new FileInputStream(privateFile));
                    buf.read(bytes, 0, bytes.length);
                    buf.close();
                    String privateTreeFileContentBase64 = Base64.encodeToString(bytes, Base64.DEFAULT);
                    FileRequestModel privateTreeRequestModel = new FileRequestModel(
                            "save private-tree",
                            privateTreeFileContentBase64,
                            new CommitterRequestModel(user.getUserName(), email)
                    );
                    Call<FileContent> privateTreeJsonCall = apiInterface.createFile(user.login, repoName + "-private",
                            "tree-private.json", privateTreeRequestModel);
                    privateTreeJsonCall.execute();
                }

                //UI Thread work here
                handler.post(() -> afterExecution.accept(deeplinkUrl));
            } catch (Exception ex) {
                Log.e(TAG, "CreateRepoTask is failed", ex);
                handler.post(() -> errorExecution.accept(ex.getLocalizedMessage()));
            }
        });
    }
}
