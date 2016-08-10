package tr.com.srdc.cda2fhir.impl;

import ca.uhn.fhir.model.dstu2.composite.ResourceReferenceDt;
import ca.uhn.fhir.model.dstu2.resource.AllergyIntolerance;
import ca.uhn.fhir.model.dstu2.resource.Bundle;
import ca.uhn.fhir.model.dstu2.resource.Bundle.Entry;
import ca.uhn.fhir.model.dstu2.resource.Composition;
import ca.uhn.fhir.model.dstu2.resource.Condition;
import ca.uhn.fhir.model.dstu2.resource.Encounter;
import ca.uhn.fhir.model.dstu2.resource.Immunization;
import ca.uhn.fhir.model.dstu2.resource.Observation;
import ca.uhn.fhir.model.dstu2.resource.Organization;
import ca.uhn.fhir.model.dstu2.resource.Patient;
import ca.uhn.fhir.model.primitive.IdDt;
import org.openhealthtools.mdht.uml.cda.Section;
import org.openhealthtools.mdht.uml.cda.SubstanceAdministration;
import org.openhealthtools.mdht.uml.cda.consol.*;
import tr.com.srdc.cda2fhir.CCDATransformer;
import tr.com.srdc.cda2fhir.ResourceTransformer;

import java.util.UUID;

/**
 * Created by mustafa on 8/3/2016.
 */
public class CCDATransformerImpl implements CCDATransformer {

    private int counter;
    private IdGeneratorEnum idGenerator;
    private ResourceTransformer resTransformer;
    private IdDt patientId;

    public CCDATransformerImpl() {
        this.counter = 0;
        // The default resource id pattern is UUID
        this.idGenerator = IdGeneratorEnum.UUID;
        resTransformer = new ResourceTransformerImpl(this);
    }

    public CCDATransformerImpl(IdGeneratorEnum idGen) {
        this();
        this.idGenerator = idGen;
    }

    @Override
    public void setIdGenerator(IdGeneratorEnum idGen) {
        this.idGenerator = idGen;
    }

    @Override
    public synchronized String getUniqueId() {
        switch (this.idGenerator) {
            case COUNTER:
                return "" + (++counter);
            case UUID:
            default:
                return UUID.randomUUID().toString();
        }
    }

    @Override
    public IdDt getPatientId() {
        return patientId;
    }

    @Override
    public Bundle transformCCD(ContinuityOfCareDocument ccd) {
        if(ccd == null)
            return null;

        // create and init the global bundle and the composition resources
        Bundle ccdBundle = new Bundle();
        Composition ccdComposition = new Composition();
        ccdComposition.setId(new IdDt("Composition", getUniqueId()));
        ccdBundle.addEntry(new Bundle.Entry().setResource(ccdComposition));

        // transform the patient data and assign it to Composition.subject
        // start of bundle-to-patient
        // Since the methods of resTransformer class have return type Bundle, we need the following lines to get the 'Patient' from the 'Bundle'
        Patient subject = null; // subject will be assigned to the appropriate entry of the bundle, we may need null-check
        Bundle subjectBundle = resTransformer.PatientRole2Patient(ccd.getRecordTargets().get(0).getPatientRole());
		for( Entry entry : subjectBundle.getEntry() ){
			if( entry.getResource() instanceof ca.uhn.fhir.model.dstu2.resource.Patient){
				subject = (ca.uhn.fhir.model.dstu2.resource.Patient) entry.getResource();
			}
		}
        // end of bundle-to-patient
		
        patientId = subject.getId();
        ccdComposition.setSubject(new ResourceReferenceDt(patientId));
        ccdBundle.addEntry(new Bundle.Entry().setResource(subject));

        for(Section cdaSec: ccd.getSections()) {
            Composition.Section fhirSec = resTransformer.section2Section(cdaSec);
            ccdComposition.addSection(fhirSec);
            if(cdaSec instanceof AdvanceDirectivesSection) {

            }
            else if(cdaSec instanceof AllergiesSection) {
            	AllergiesSection allSec = (AllergiesSection) cdaSec;
            	for(AllergyProblemAct probAct : allSec.getAllergyProblemActs())
            	{
            		AllergyIntolerance allergyIntolerance = null;

            		// getting allergyIntolerance from observation bundle
            		Bundle allergyIntoleranceBundle = resTransformer.AllergyProblemAct2AllergyIntolerance(probAct);
    				for(ca.uhn.fhir.model.dstu2.resource.Bundle.Entry entry : allergyIntoleranceBundle.getEntry()){
    					if( entry.getResource() instanceof AllergyIntolerance )
    						allergyIntolerance = (AllergyIntolerance) entry.getResource();
    				}
    				
    				// we may need to check if allergyIntolerance is null
    				
            		ResourceReferenceDt ref = fhirSec.addEntry();
        			ref.setReference(allergyIntolerance.getId());
        			ccdBundle.addEntry(new Bundle.Entry().setResource(allergyIntolerance));
        			
            	}
            }
            else if(cdaSec instanceof EncountersSection) {

            }
            else if(cdaSec instanceof FamilyHistorySection) {

            }
            else if(cdaSec instanceof FunctionalStatusSection) {

            }
            else if(cdaSec instanceof ImmunizationsSection) {
            	ImmunizationsSection immSec = (ImmunizationsSection) cdaSec;
            	for(SubstanceAdministration subAd : immSec.getSubstanceAdministrations())
            	{
            		Bundle immunization = resTransformer.SubstanceAdministration2Immunization(subAd);
            	    ResourceReferenceDt ref = fhirSec.addEntry();
                    ref.setReference(immunization.getId());
                    ccdBundle.addEntry(new Bundle.Entry().setResource(immunization));
            	}
            }
            else if(cdaSec instanceof MedicalEquipmentSection) {

            }
            else if(cdaSec instanceof MedicationsSection) {

            }
            else if(cdaSec instanceof PayersSection) {

            }
            else if(cdaSec instanceof PlanOfCareSection) {

            }
            else if(cdaSec instanceof ProblemSection) {
                ProblemSection probSec = (ProblemSection) cdaSec;
                for(ProblemConcernAct pcAct: probSec.getConsolProblemConcerns()) {
                    Bundle condition = resTransformer.ProblemConcernAct2Condition(pcAct);
                    ResourceReferenceDt ref = fhirSec.addEntry();
                    ref.setReference(condition.getId());
                    ccdBundle.addEntry(new Bundle.Entry().setResource(condition));
                    
                }
            }
            else if(cdaSec instanceof ProceduresSection) {

            }
            else if(cdaSec instanceof ResultsSection) {
            	ResultsSection resultSec = (ResultsSection) cdaSec;
            	for(ResultOrganizer resOrg : resultSec.getResultOrganizers())
            	{
            		for(ResultObservation resObs : resOrg.getResultObservations())
            		{
            			Bundle observation = resTransformer.ResultObservation2Observation(resObs);
            			ResourceReferenceDt ref = fhirSec.addEntry();
            			ref.setReference(observation.getId());
            			ccdBundle.addEntry(new Bundle.Entry().setResource(observation));
            		}
            	}

            }
            else if(cdaSec instanceof SocialHistorySection) {

            }
            else if(cdaSec instanceof VitalSignsSection) {
            	VitalSignsSection vitalSec = (VitalSignsSection) cdaSec;
            	for(VitalSignsOrganizer vsOrg : vitalSec.getVitalSignsOrganizers())
            	{
            		for(VitalSignObservation vsObs : vsOrg.getVitalSignObservations())
            		{
            			Observation observation = null;
            			
            			// getting allergyIntolerance from observation bundle
            			Bundle observationBundle = resTransformer.VitalSignObservation2Observation(vsObs);
            			for(ca.uhn.fhir.model.dstu2.resource.Bundle.Entry entry : observationBundle.getEntry()){
            				if( entry.getResource() instanceof Observation )
            					observation = (Observation) entry.getResource();
            			}

            			// we may need to check if observation is null
            			
            			ResourceReferenceDt ref = fhirSec.addEntry();
            			ref.setReference(observation.getId());
            			ccdBundle.addEntry(new Bundle.Entry().setResource(observation));
            		}
            	}
            }
        }

        return ccdBundle;
    }
}
