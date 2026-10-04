package lccast.voting.system.dto.analytics;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AnalyticsDTO {

    public static class CandidateVotes {
        public String name;
        public long votes;
        public String position;
        public String photo;

        public CandidateVotes(String name, long votes, String position, String photo) {
            this.name = name;
            this.votes = votes;
            this.position = position;
            this.photo = photo;
        }
    }

    public static class PartylistVotes {
        public String name;
        public long votes;

        public PartylistVotes(String name, long votes) {
            this.name = name;
            this.votes = votes;
        }
    }

    public static class LabeledSeries {
        public List<String> labels;
        public List<Long> values;

        public LabeledSeries(List<String> labels, List<Long> values) {
            this.labels = labels;
            this.values = values;
        }
    }

    public static class ProgramVotes {
        public String program;
        public long votes;
        public String color;

        public ProgramVotes(String program, long votes, String color) {
            this.program = program;
            this.votes = votes;
            this.color = color;
        }
    }

    public static class CampusAnalytics {
        public String campus;
        public String electionId;
        public String electionTitle;
        public long totalVoters;
        public long votesCast;
        public List<CandidateVotes> candidates;
        public List<PartylistVotes> partyLists; // null for department
        public List<ProgramVotes> programVotes; // null for department
        public List<ProgramVotes> programNotVoted;
        public LabeledSeries yearLevel;
        public LabeledSeries activity;
        public LabeledSeries status; // null for SSC
        public String votingType;
        public String schoolYear;
        public String phase;
        public String parentElectionId;
        public boolean drawElection;
        public List<String> drawPositions = List.of();
    }

    public static class SSCAnalytics {
        public Map<String, CampusAnalytics> campuses; // key = campus code
        public Map<String, Map<String, CampusAnalytics>> elections;
    }

    public static class DepartmentAnalytics {
        public Map<String, Map<String, CampusAnalytics>> departments; // deptCode -> campusCode -> data
    }

    public static class FullAnalytics {
        public SSCAnalytics ssc;
        public DepartmentAnalytics departments;
    }

    public static class AdminDeptAnalyticsResponse {
        public UUID departmentId;
        public String departmentName;
        public String departmentTitle;
        public String votingType;
        public boolean hasActiveElection;
        public CampusAnalytics analytics; // null if no active election
        public List<CampusAnalytics> history;
    }
}