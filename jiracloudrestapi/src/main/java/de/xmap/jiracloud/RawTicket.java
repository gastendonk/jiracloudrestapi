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
		developmentDescription = new StaticDocField(issue, JiraCloudAccess.cf_developmentDescription);
		changeNotesDescription = new StaticDocField(issue, JiraCloudAccess.cf_changeNotesDescription);
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
		/** plain text or HTML, can be null */
		private final String text;
		/** null if field does not exist or plain text */
		private final Set<JiraImage> images;
		
		public StaticDocField(IssueAccess issue, String id) {
			JSONObject jo = issue.getJSONObject();
			String path = "/fields/" + id;
			
			// Does field exist?
			if (jo.query(path) == null) {
				plainText = true;
				text = null;
				images = null;
			} else {
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
		}
		
		public static Set<JiraImage> extractImages(String html) {
			Set<JiraImage> images = new HashSet<>();
			if (html != null) {
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
	                            images.add(new JiraImageImpl(src));
	                        }
	                    }
	                }
	                o = html.indexOf("<img", oo);
	            }
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
	
	public interface JiraImage {
		
		String getSrc();
		
		String getFilename();
		
		/**
		 * May be called before calling getImage().
		 * @param jira -
		 */
		void loadFromJira(JiraCloudAccess jira);
		
		byte[] getImage();
	}
	
	public static class JiraImageImpl implements JiraImage {
		private final String src;
		private byte[] image = null;
		
		public JiraImageImpl(String src) {
			this.src = src;
		}
		
		@Override
		public String getSrc() {
			return src;
		}

		/**
		 * @return file name without path and with suffix
		 */
		@Override
		public String getFilename() {
			return filename(src);
		}
		
		/**
		 * @param src -
		 * @return file name without path and with suffix
		 */
		public static String filename(String src) {
			String ret = src.substring(src.lastIndexOf("/") + 1);
			String sx = ret.toLowerCase();
			// Man könnte den Suffix auch dem <img alt="..."> Attribut entnehmen.
			if (!sx.endsWith(".png") && !sx.endsWith(".jpg") && !sx.endsWith(".jpeg") && !sx.endsWith(".gif")) {
				ret += ".png"; // Annahme, dass es meist PNG ist.
			}
			return ret;
		}

		@Override
		public void loadFromJira(JiraCloudAccess jira) {
			if (image == null) {
				image = jira.loadImage(src);
			}
		}

		@Override
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
			JiraImageImpl other = (JiraImageImpl) obj;
			return Objects.equals(src, other.src);
		}
	}
}
