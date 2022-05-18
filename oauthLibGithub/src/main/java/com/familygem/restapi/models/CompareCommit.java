package com.familygem.restapi.models;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class CompareCommit {
    @SerializedName("status")
    @Expose
    public String status;
    @SerializedName("ahead_by")
    @Expose
    public Integer aheadBy;
    @SerializedName("behind_by")
    @Expose
    public Integer behindBy;
    @SerializedName("total_commits")
    @Expose
    public Integer totalCommits;
}
