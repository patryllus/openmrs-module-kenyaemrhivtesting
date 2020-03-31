package org.openmrs.module.hivtestingservices.api.shr;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.openmrs.Concept;
import org.openmrs.Encounter;
import org.openmrs.EncounterType;
import org.openmrs.Obs;
import org.openmrs.Order;
import org.openmrs.OrderType;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.PersonAddress;
import org.openmrs.PersonAttributeType;
import org.openmrs.PersonName;
import org.openmrs.api.ConceptService;
import org.openmrs.api.EncounterService;
import org.openmrs.api.ObsService;
import org.openmrs.api.OrderService;
import org.openmrs.api.PatientService;
import org.openmrs.api.PersonService;
import org.openmrs.api.context.Context;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class CovidLabDataExchange {

    PersonService personService = Context.getPersonService();
    PatientService patientService = Context.getPatientService();
    ObsService obsService = Context.getObsService();
    ConceptService conceptService = Context.getConceptService();
    EncounterService encounterService = Context.getEncounterService();
    String patientIdentifier;

    String TELEPHONE_CONTACT = "b2c38640-2603-4629-aebd-3b54f33f1e3a";
    String TEST_ORDER_TYPE_UUID = "52a447d3-a64a-11e3-9aeb-50e549534c5e";


    /**
     * Returns a list of active lab requests
     * @return
     */
    public ArrayNode getCovidLabRequests() {

        JsonNodeFactory factory = OutgoingPatientSHR.getJsonNodeFactory();
        ArrayNode activeRequests = factory.arrayNode();
        Set<Integer> allPatients = getPatientsWithOrders();

        if (!allPatients.isEmpty()) {

            for (Integer ptId : allPatients) {
                Patient p = patientService.getPatient(ptId);
                activeRequests = getActiveLabRequestForPatient(p, activeRequests);
            }
        }
        return activeRequests;

    }

    /**
     * Returns active lab requests for a patient
     * @param patient
     * @return
     */
    public ArrayNode getActiveLabRequestForPatient(Patient patient, ArrayNode requests) {

        JsonNodeFactory factory = OutgoingPatientSHR.getJsonNodeFactory();
        ObjectNode patientSHR = factory.objectNode();


        if (patient != null) {
            return getActiveLabRequestsForPatient(patient, requests);

        } else {
            return requests;
        }
    }

    /**
     * Returns a person's phone number attribute
     * @param patient
     * @return
     */
    private String getPatientPhoneNumber(Patient patient) {
        PersonAttributeType phoneNumberAttrType = personService.getPersonAttributeTypeByUuid(TELEPHONE_CONTACT);
        return patient.getAttribute(phoneNumberAttrType) != null ? patient.getAttribute(phoneNumberAttrType).getValue() : "";
    }

    /**
     * Returns a patient's address
     * @param patient
     * @return
     */
    private ObjectNode getPatientAddress(Patient patient) {

        /**
         * county: personAddress.country
         * sub-county: personAddress.stateProvince
         * ward: personAddress.address4
         * landmark: personAddress.address2
         * postal address: personAddress.address1
         */

        Set<PersonAddress> addresses = patient.getAddresses();
        //patient address
        ObjectNode patientAddressNode = OutgoingPatientSHR.getJsonNodeFactory().objectNode();
        ObjectNode physicalAddressNode = OutgoingPatientSHR.getJsonNodeFactory().objectNode();
        String postalAddress = "";
        String county = "";
        String sub_county = "";
        String ward = "";
        String landMark = "";

        for (PersonAddress address : addresses) {
            if (address.getAddress1() != null) {
                postalAddress = address.getAddress1();
            }
            if (address.getCountry() != null) {
                county = address.getCountry() != null ? address.getCountry() : "";
            }

            if (address.getCountyDistrict() != null) {
                county = address.getCountyDistrict() != null ? address.getCountyDistrict() : "";
            }

            if (address.getStateProvince() != null) {
                sub_county = address.getStateProvince() != null ? address.getStateProvince() : "";
            }

            if (address.getAddress4() != null) {
                ward = address.getAddress4() != null ? address.getAddress4() : "";
            }
            if (address.getAddress2() != null) {
                landMark = address.getAddress2() != null ? address.getAddress2() : "";
            }

        }

        physicalAddressNode.put("COUNTY", county);
        physicalAddressNode.put("SUB_COUNTY", sub_county);
        physicalAddressNode.put("WARD", ward);
        physicalAddressNode.put("NEAREST_LANDMARK", landMark);

        //combine all addresses
        patientAddressNode.put("PHYSICAL_ADDRESS", physicalAddressNode);
        patientAddressNode.put("POSTAL_ADDRESS", postalAddress);

        return patientAddressNode;
    }

    /**
     * Returns patient name
     * @param patient
     * @return
     */
    private ObjectNode getPatientName(Patient patient) {
        PersonName pn = patient.getPersonName();
        ObjectNode nameNode = OutgoingPatientSHR.getJsonNodeFactory().objectNode();
        nameNode.put("FIRST_NAME", pn.getGivenName());
        nameNode.put("MIDDLE_NAME", pn.getMiddleName());
        nameNode.put("LAST_NAME", pn.getFamilyName());
        return nameNode;
    }

    private ObjectNode getPatientIdentifier(Patient patient) {

        PatientIdentifierType NATIONAL_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(SHRConstants.NATIONAL_ID);
        PatientIdentifierType ALIEN_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SHRConstants.ALIEN_NUMBER);
        PatientIdentifierType PASSPORT_NUMBER_TYPE = patientService.getPatientIdentifierTypeByUuid(SHRConstants.PASSPORT_NUMBER);
        PatientIdentifierType CASE_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(SHRConstants.PATIENT_CLINIC_NUMBER);
        PatientIdentifierType OPENMRS_ID_TYPE = patientService.getPatientIdentifierTypeByUuid(SHRConstants.MEDICAL_RECORD_NUMBER);

        List<PatientIdentifier> identifierList = patientService.getPatientIdentifiers(null, Arrays.asList(NATIONAL_ID_TYPE, NATIONAL_ID_TYPE, ALIEN_NUMBER_TYPE, PASSPORT_NUMBER_TYPE), null, Arrays.asList(patient), null);

        ObjectNode patientIdentifiers = OutgoingPatientSHR.getJsonNodeFactory().objectNode();

        for (PatientIdentifier identifier : identifierList) {
            PatientIdentifierType identifierType = identifier.getIdentifierType();

            if (identifierType.equals(NATIONAL_ID_TYPE)) {
                patientIdentifiers.put("type", 1);
                patientIdentifiers.put("identifier", identifier.getIdentifier());
                return patientIdentifiers;

            } else if (identifierType.equals(ALIEN_NUMBER_TYPE)) {
                patientIdentifiers.put("type", 3);
                patientIdentifiers.put("identifier", identifier.getIdentifier());
                return patientIdentifiers;


            } else if (identifierType.equals(PASSPORT_NUMBER_TYPE)) {
                patientIdentifiers.put("type", 2);
                patientIdentifiers.put("identifier", identifier.getIdentifier());
                return patientIdentifiers;

            } else if (identifierType.equals(CASE_ID_TYPE) || identifierType.equals(OPENMRS_ID_TYPE)) { // use this to track those with no documented identifier
                patientIdentifiers.put("type", 4);
                patientIdentifiers.put("identifier", identifier.getIdentifier());
                return patientIdentifiers;
            }

        }
        return patientIdentifiers;
    }

    /**
     * Returns object lab request for patients
     * @param patient
     * @return
     */
    protected ArrayNode getActiveLabRequestsForPatient(Patient patient, ArrayNode labTests) {

        ObjectNode cifInfo = getCovidEnrollmentDetails(patient);
        ObjectNode address = getPatientAddress(patient);
        ArrayNode blankArray = OutgoingPatientSHR.getJsonNodeFactory().arrayNode();
        OrderService orderService = Context.getOrderService();
        Integer caseId = patient.getPatientId();
        ObjectNode idMap = getPatientIdentifier(patient);
        //Check whether client has active covid order
        OrderType patientLabOrders = orderService.getOrderTypeByUuid(TEST_ORDER_TYPE_UUID);
        String dob = patient.getBirthdate() != null ? OutgoingPatientSHR.getSimpleDateFormat("yyyy-MM-dd").format(patient.getBirthdate()) : "";
        String deathDate = patient.getDeathDate() != null ? OutgoingPatientSHR.getSimpleDateFormat("yyyy-MM-dd").format(patient.getDeathDate()) : "";

        //ArrayNode labTests = OutgoingPatientSHR.getJsonNodeFactory().arrayNode();
        if (patientLabOrders != null) {
            //Get active lab orders
            List<Order> activeVLTestOrders = orderService.getActiveOrders(patient, patientLabOrders, null, null);
            if (activeVLTestOrders.size() > 0) {
                for (Order o : activeVLTestOrders) {
                    ObjectNode test = OutgoingPatientSHR.getJsonNodeFactory().objectNode();
                    test.put("case_id", caseId);
                    test.put("identifier_type", idMap.get("type"));
                    test.put("identifier", idMap.get("identifier"));
                    test.put("patient_name", patient.getGivenName() + " " + patient.getFamilyName() + " " + patient.getMiddleName());
                    test.put("justification", "");
                    test.put("county", cifInfo.get("county"));
                    test.put("subcounty", cifInfo.get("subCounty"));
                    test.put("ward", "");
                    test.put("residence", address.get("POSTAL_ADDRESS"));
                    test.put("sex", patient.getGender());
                    test.put("health_status", cifInfo.get("healthStatus"));
                    test.put("date_symptoms", "");
                    test.put("date_admission", "");
                    test.put("date_isolation", "");
                    test.put("date_death", deathDate);
                    test.put("date_birth", dob);
                    test.put("lab_id", "");
                    test.put("test_type_id",o.getOrderReason() != null ? getOrderReasonCode(o.getOrderReason().getConceptId()) : "");
                    test.put("occupation", "");
                    test.put("temperature", cifInfo.get("temp"));
                    test.put("sample_type", o.getInstructions() != null ? getSampleTypeCode(o.getInstructions()) : "");
                    test.put("symptoms", blankArray);
                    test.put("observed_signs", blankArray);
                    test.put("underlying_conditions", blankArray);
                    labTests.add(test);
                }
            }
        }

        return labTests;
    }

    private String getSampleTypeCode(String type) {

        if (type == null) {
            return "";
        }
        Integer code;
        if (type.equals("Blood")) {
            code = 3;
        } else if (type.equals("OP Swab")) {
            code = 2;
        }  else if (type.equals("Tracheal Aspirate")) {
            code = 5;
        } else if (type.equals("Sputum")) {
            code = 4;
        } else if (type.equals("NP Swab")) {
            code = 1;
        } else {
            code = 6;
        }
       return code.toString();
    }

    /**
     * Converter for concept to lab system code
     * @param orderReason
     * @return
     */
    private String getOrderReasonCode(Integer orderReason) {

        if (orderReason == null)
            return "";

        Integer code = null;
        if (orderReason.equals(162080)) { // baseline
            code =1;
        } else if (orderReason.equals(162081)) { // 1st followup
            code = 2;
        } else if (orderReason.equals(164142)) { // 2nd followup
            code = 3;
        } else if (orderReason.equals(159490)) { // 3rd followup
            code = 4;
        } else if (orderReason.equals(159489)) { // 4th followup
            code = 5;
        } else if (orderReason.equals(161893)) { // 5th followup
            code = 6;
        }
       return code != null ? code.toString() : "";
    }


    private ObjectNode getCovidEnrollmentDetails(Patient patient) {
        /*public List<Obs> getObservations(List<Person> whom, List<Encounter> encounters, List<Concept> questions,
	        List<Concept> answers, List<PERSON_TYPE> personTypes, List<Location> locations, List<String> sort,
	        Integer mostRecentN, Integer obsGroupId, Date fromDate, Date toDate, boolean includeVoidedObs,
	        String accessionNumber) throws APIException;*/

        /*<obs id="status-at-reporting" conceptId="159640AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA" labelText=" "
                             answerConceptIds="159405AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,159407AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,160432AAAAAAAAAAAAAAAAAAAAAAAAAAAAAA,1067AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"
                             style="radio" answerLabels="Stable,Severly ill,Dead,Unknown"/>*/

        Concept countyConcept = conceptService.getConcept(165197);
        Concept subCountyConcept = conceptService.getConcept(161551);
        Concept healthStatusConcept = conceptService.getConcept(159640);
        Concept tempConcept = conceptService.getConcept(5088);
        ObjectNode enrollmentObj = OutgoingPatientSHR.getJsonNodeFactory().objectNode();


        String county = "", subCounty = "";
        Integer healthStatus = null;
        Double temp = null;

        String COVID_19_CASE_INVESTIGATION = "a4414aee-6832-11ea-bc55-0242ac130003";

        EncounterType covid_enc_type = encounterService.getEncounterTypeByUuid(COVID_19_CASE_INVESTIGATION);
        Encounter lastEncounter = lastEncounter(patient, covid_enc_type);

        List<Concept> questionConcepts = Arrays.asList(countyConcept, subCountyConcept, healthStatusConcept, tempConcept);
        List<Obs> enrollmentData = obsService.getObservations(
                Collections.singletonList(patient.getPerson()),
                Collections.singletonList(lastEncounter),
                questionConcepts,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false
        );

        for(Obs o: enrollmentData) {
            if (o.getConcept().equals(countyConcept) ) {
                county = o.getValueText();
            } else if (o.getConcept().equals(subCountyConcept)) {
                subCounty = o.getValueText();
            } else if (o.getConcept().equals(healthStatusConcept)) {
                if (o.getValueCoded().getConceptId().equals(159405)) {
                    //healthStatus = "Stable";
                    healthStatus =1;
                } else if (o.getValueCoded().getConceptId().equals(159407)) {
                    //healthStatus = "Severely ill";
                    healthStatus = 2;

                } else if (o.getValueCoded().getConceptId().equals(160432)) {
                    //healthStatus = "Dead";
                    healthStatus = 3;
                } else if (o.getValueCoded().getConceptId().equals(1067)) {
                    //healthStatus = "Unknown";
                    healthStatus =4;
                }
            } else if (o.getConcept().equals(tempConcept)) {
                temp = o.getValueNumeric();
            }
        }

        enrollmentObj.put("county", county);
        enrollmentObj.put("subCounty", subCounty);
        enrollmentObj.put("healthStatus", healthStatus != null ? healthStatus.toString() : "");
        enrollmentObj.put("temp", temp != null ? temp.toString() : "");
        return enrollmentObj;
    }
    /**
     * Finds the last encounter during the program enrollment with the given encounter type
     *
     * @param type the encounter type
     *
     * @return the encounter
     */
    public Encounter lastEncounter(Patient patient, EncounterType type) {
        List<Encounter> encounters = Context.getEncounterService().getEncounters(patient, null, null, null, null, Collections.singleton(type), null, null, null, false);
        return encounters.size() > 0 ? encounters.get(encounters.size() - 1) : null;
    }

    /**
     * Returns a list of patients with active lab orders
     * @return
     */
    protected Set<Integer> getPatientsWithOrders() {

        Set<Integer> patientWithActiveLabs = new HashSet<Integer>();
        String sql = "select patient_id from orders where order_action='NEW' and instructions is not null and voided=0;";
        List<List<Object>> activeOrders = Context.getAdministrationService().executeSQL(sql, true);
        if (!activeOrders.isEmpty()) {
            for (List<Object> res : activeOrders) {
                Integer patientId = (Integer) res.get(0);
                patientWithActiveLabs.add(patientId);
            }
        }
        return patientWithActiveLabs;
    }

}