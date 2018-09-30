package com.seriousbisiness.jira.plugin.reports;

import com.atlassian.jira.component.ComponentAccessor;
import com.atlassian.jira.datetime.DateTimeFormatter;
import com.atlassian.jira.datetime.DateTimeFormatterFactory;
import com.atlassian.jira.datetime.DateTimeStyle;
import com.atlassian.jira.issue.Issue;
import com.atlassian.jira.issue.search.SearchException;
import com.atlassian.jira.issue.search.SearchProvider;
import com.atlassian.jira.issue.search.SearchResults;
import com.atlassian.jira.jql.builder.JqlQueryBuilder;
import com.atlassian.jira.plugin.report.impl.AbstractReport;
import com.atlassian.jira.project.ProjectManager;
import com.atlassian.jira.security.groups.GroupManager;
import com.atlassian.jira.user.ApplicationUser;
import com.atlassian.jira.util.ParameterUtils;
import com.atlassian.jira.web.FieldVisibilityManager;
import com.atlassian.jira.web.action.ProjectActionSupport;
import com.atlassian.jira.web.bean.PagerFilter;
import com.atlassian.plugin.spring.scanner.annotation.component.Scanned;
import com.atlassian.plugin.spring.scanner.annotation.imports.JiraImport;
import com.atlassian.query.Query;
import com.google.common.collect.ImmutableMap;
import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 *
 * @author alex
 */
@Scanned
public class IssueReport extends AbstractReport {

    private static final Logger log = Logger.getLogger(IssueReport.class);

    @JiraImport
    private final SearchProvider searchProvider;
    @JiraImport
    private final ProjectManager projectManager;
    @JiraImport
    private final FieldVisibilityManager fieldVisibilityManager;
    private final DateTimeFormatter formatter;
    private final GroupManager groupManager;

    private Long projectId;
    private Date endDate;


    public IssueReport(SearchProvider searchProvider,
                       ProjectManager projectManager,
                       FieldVisibilityManager fieldVisibilityManager,
                       @JiraImport DateTimeFormatterFactory dateTimeFormatterFactory) {

        this.searchProvider = searchProvider;
        this.projectManager = projectManager;
        this.fieldVisibilityManager = fieldVisibilityManager;
        this.formatter = dateTimeFormatterFactory.formatter().withStyle(DateTimeStyle.DATE).forLoggedInUser();
        this.groupManager = ComponentAccessor.getGroupManager();
    }

    public String generateReportHtml(ProjectActionSupport action, Map map) throws Exception {
        final Map startingParams = ImmutableMap.builder()
                .put("action", action)
                .put("issues", getReportData(action))
                .put("fieldVisibility", fieldVisibilityManager)
                .put("portlet", this)
                .put("formatter", formatter)
                .build();

        return descriptor.getHtml("view", startingParams);
    }

    /**
     * @return issues in selected project where Due Date < endDate
     */
    private List<Issue> getReportData(ProjectActionSupport action) throws SearchException {
        JqlQueryBuilder queryBuilder = JqlQueryBuilder.newBuilder();
        Query query = queryBuilder.where().due().lt(endDate).and().project(projectId).buildQuery();
        SearchResults results = searchProvider.search(query, action.getLoggedInUser(), PagerFilter.getUnlimitedFilter());
        return results.getIssues();
    }

    /**
     * Only for project managers
     */
    @Override
    public boolean showReport() {
        ApplicationUser user = ComponentAccessor.getJiraAuthenticationContext().getLoggedInUser();
        return groupManager.isUserInGroup(user, "project-managers");
    }

    @Override
    public void validate(ProjectActionSupport action, Map params) {
        projectId = ParameterUtils.getLongParam(params, "selectedProjectId");
        if (projectId == null || projectManager.getProjectObj(projectId) == null){
            action.addError("selectedProjectId", action.getText("report.issuecreation.projectid.invalid"));
            log.error("Invalid projectId");
        }

        try {
            endDate = formatter.parse(ParameterUtils.getStringParam(params, "dueDate"));
        } catch (IllegalArgumentException e) {
            log.warn("Exception while parsing endDate! endDate=" + String.valueOf(endDate));
            endDate = new Date();
        }

        super.validate(action, params);
    }
}
