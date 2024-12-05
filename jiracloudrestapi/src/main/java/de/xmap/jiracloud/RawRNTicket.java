package de.xmap.jiracloud;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;
import de.xmap.jiracloud.RawTicket.StaticDocField;

/**
 * IssueAccess wrapper with access to 'Release note ticket' fields. 
 * Call loadAllImagesFromJira(["de"|"en"]) for loading all images.
 */
public class RawRNTicket {
	public static final String DE = "de";
	public static final String EN = "en";
	private final IssueAccess issue;
	private final StaticDocFieldML rns = new StaticDocFieldML(JiraCloudAccess.cf_rns_de, JiraCloudAccess.cf_rns_en);
	private final StaticDocFieldML rnd = new StaticDocFieldML(JiraCloudAccess.cf_rnd_de, JiraCloudAccess.cf_rnd_en);
	
	public RawRNTicket(IssueAccess issue) {
		this.issue = issue;
	}
	
	public static String fieldnamesForQueryExtension() {
		return  JiraCloudAccess.cf_rnt_de + "," + JiraCloudAccess.cf_rnt_en + "," + //
				JiraCloudAccess.cf_rns_de + "," + JiraCloudAccess.cf_rns_en + "," + //
				JiraCloudAccess.cf_rnd_de + "," + JiraCloudAccess.cf_rnd_en;
	}

	public IssueAccess getIssue() {
		return issue;
	}

	public void loadAllImagesFromJira() {
		JiraCloudAccess jira = issue.jira();
		rns.de().loadAllImagesFromJira(jira);
		rns.en().loadAllImagesFromJira(jira);
		rnd.de().loadAllImagesFromJira(jira);
		rnd.en().loadAllImagesFromJira(jira);
	}
	
	/**
	 * @param lang DE or EN
	 */
	public void loadAllImagesFromJira(String lang) {
		JiraCloudAccess jira = issue.jira();
		rns.get(lang).loadAllImagesFromJira(jira);
		rnd.get(lang).loadAllImagesFromJira(jira);
	}

	/**
	 * @param lang DE or EN
	 * @return Release notes title
	 */
	public String getRnt(String lang) {
		return issue.text("/fields/" + (DE.equals(lang) ? JiraCloudAccess.cf_rnt_de : JiraCloudAccess.cf_rnt_en));
	}
	
	/**
	 * @return Release notes summary
	 */
	public StaticDocFieldML getRns() {
		return rns;
	}

	/**
	 * @return Release notes details
	 */
	public StaticDocFieldML getRnd() {
		return rnd;
	}
	
	public StaticDocField getDevelopmentDescription() {
		return new StaticDocField(JiraCloudAccess.cf_developmentDescription, issue);
	}

	/**
	 * Multi-language StaticDocFields
	 */
	public class StaticDocFieldML {
		private final StaticDocField de;
		private final StaticDocField en;
		
		StaticDocFieldML(String deId, String enId) {
			de = new StaticDocField(deId, issue);
			en = new StaticDocField(enId, issue);
		}

		public StaticDocField de() {
			return de;
		}

		public StaticDocField en() {
			return en;
		}

		public StaticDocField get(String lang) {
			return switch (lang) {
				case DE -> de;
				case EN -> en;
				default -> throw new IllegalArgumentException("Parameter lang must be \"" + DE + "\" or \"" + EN + "\"!");
			};
		}
	}
}
