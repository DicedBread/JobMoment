package diced.bread.client.prospledata;

import java.util.ArrayList;
import java.util.Date;

public class Opportunity{
    public String id;
    public String groupContentID;
    // public Object url;
    public String title;
    public String applyByUrl;
    public boolean expired;
    public Overview overview;
    public ArrayList<OpportunityType> opportunityTypes;
    public String locationDescription;
    public Object applicationsOpenDate;
    public Date applicationsCloseDate;
    public String applicationsCloseDateDescription;
    public StartDate startDate;
    public String timeZone;
    // public ArrayList<StudyField> studyFields;
    public String detailPageURL;
    public boolean sponsored;
    // public ArrayList<PhysicalLocation> physicalLocations;
    public ArrayList<Object> remoteWorkLocations;
    public boolean remoteAvailable;
    public ParentEmployer parentEmployer;
    public Object minSalary;
    public Object maxSalary;
    public Object salary;
    public Object additionalBenefits;
    public Object salaryDescription;
    public boolean hideSalary;
    // public SalaryCurrency salaryCurrency;
    public Object flexibleCTAs;
    // public ArrayList<WorkingRight> workingRights;
    public Object minNumberVacancies;
    public Object maxNumberVacancies;
    public boolean acceptsPreRegisters;
    public Object externalTalentPool;
    public boolean applicationsOpen;
    public ApplicationProcess applicationProcess;
    public ArrayList<DegreeType> degreeTypes;
    public ArrayList<InstitutionLocation> institutionLocations;
    public Object minimumGrades;
    public String __typename;
    public ArrayList<Object> pathways;
}