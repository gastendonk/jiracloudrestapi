package de.xmap.jiracloud;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import de.xmap.jiracloud.JiraCloudAccess.IssueAccess;
import kong.unirest.json.JSONArray;
import kong.unirest.json.JSONObject;

/**
 * IssueAccess wrapper with access to 'development description' and 'chage notes description'.
 * Call loadAllImagesFromJira() for loading all images.
 */
public class RawTicket {
	private final IssueAccess issue;
	private final StaticDocField developmentDescription;
	private final StaticDocField changeNotesDescription;
	
	public RawTicket(IssueAccess issue) {
		this.issue = issue;
		developmentDescription = new StaticDocField(JiraCloudAccess.cf_developmentDescription, issue);
		changeNotesDescription = new StaticDocField(JiraCloudAccess.cf_changeNotesDescription, issue);
	}
	
	public static String fieldnamesForQueryExtension() {
		return JiraCloudAccess.cf_developmentDescription + "," + JiraCloudAccess.cf_changeNotesTitle + "," + JiraCloudAccess.cf_changeNotesDescription;
	}

	public IssueAccess getIssue() {
		return issue;
	}
	
	public StaticDocField getDevelopmentDescription() {
		return developmentDescription;
	}

	public StaticDocField getChangeNotesDescription() {
		return changeNotesDescription;
	}
	
	public void loadAllImagesFromJira() {
		JiraCloudAccess jira = issue.jira();
		developmentDescription.loadAllImagesFromJira(jira);
		changeNotesDescription.loadAllImagesFromJira(jira);
	}

	public static class StaticDocField {
		private final boolean plainText;
		/** plain text or HTML */
		private final String text;
		/** null if plain text */
		private final Set<JiraImage> images;
		
		StaticDocField(String id, IssueAccess issue) {
			JSONObject jo = issue.getJSONObject();
			String path = "/fields/" + id;
			
			// is plain text?
            JSONArray content = (JSONArray) jo.query(path + "/content");
            plainText = content.length() == 1 && "paragraph".equals((String) jo.query(path + "/content/0/type"));

			if (plainText) {
				text = (String) jo.query(path + "/content/0/content/0/text");
				images = null;
			} else { // HTML
	            text = issue.text("/renderedFields/" + id);
	            images = extractImages(text);
			}
		}
		
		private static Set<JiraImage> extractImages(String html) {
			Set<JiraImage> images = new HashSet<>();
            int o = html.indexOf("<img");
            while (o >= 0) {
                int oo = html.indexOf(">", o);
                if (oo >= 0) {
                    int ooo = html.indexOf("src=\"", o);
                    if (ooo >= 0 && ooo < oo) {
                        ooo += "src=\"".length();
                        int oooo = html.indexOf("\"", ooo);
                        if (oooo > ooo) {
                        	String src = html.substring(ooo, oooo);
                            images.add(new JiraImage(src));
                        }
                    }
                }
                o = html.indexOf("<img", oo);
            }
            return images;
		}
		
        public boolean isPlainText() {
			return plainText;
		}
        
        public boolean hasImages() {
        	return images != null && !images.isEmpty();
        }

        /**
         * @return isPlainText() is false: plain text, true: HTML
         */
		public String getText() {
			return text;
		}

		/**
		 * @return null if plain text
		 */
		public Set<JiraImage> getImages() {
			return images;
		}
		
		public void loadAllImagesFromJira(JiraCloudAccess jira) {
			if (images != null) {
				images.forEach(image -> image.loadFromJira(jira));
			}
		}
	}
	
	public static class JiraImage {
		private final String src;
		private byte[] image = null;
		
		JiraImage(String src) {
			this.src = src;
		}
		
		public String getSrc() {
			return src;
		}

		/**
		 * @return file name without path and with suffix
		 */
		public String getFilename() {
			String ret = src.substring(src.lastIndexOf("/") + 1);
			String sx = ret.toLowerCase();
			// Man könnte den Suffix auch dem <img alt="..."> Attribut entnehmen.
			if (!sx.endsWith(".png") && !sx.endsWith(".jpg") && !sx.endsWith(".jpeg") && !sx.endsWith(".gif")) {
				ret += ".png"; // Annahme, dass es meist PNG ist.
			}
			return ret;
		}

		public void loadFromJira(JiraCloudAccess jira) {
			if (image == null) {
				image = jira.loadImage(src);
			}
		}

		public byte[] getImage() {
			return image;
		}
		
		public void save(File file) {
			try (FileOutputStream w = new FileOutputStream(file)) {
				w.write(image);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		@Override
		public int hashCode() {
			return Objects.hash(src);
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			JiraImage other = (JiraImage) obj;
			return Objects.equals(src, other.src);
		}
	}
}
