package org.bahmni.module.bahmnicore.web.v1_0.controller;


import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.NonUniqueObjectException;
import org.openmrs.Patient;
import org.openmrs.PatientIdentifier;
import org.openmrs.PatientIdentifierType;
import org.openmrs.Person;
import org.openmrs.Relationship;
import org.openmrs.RelationshipType;
import org.openmrs.api.ValidationException;
import org.openmrs.api.context.Context;
import org.openmrs.api.context.ContextAuthenticationException;
import org.openmrs.module.emrapi.encounter.DateMapper;
import org.openmrs.module.emrapi.patient.EmrPatientProfileService;
import org.openmrs.module.emrapi.patient.PatientProfile;
import org.openmrs.module.idgen.webservices.services.IdentifierSourceServiceWrapper;
import org.openmrs.module.webservices.rest.SimpleObject;
import org.openmrs.module.webservices.rest.web.ConversionUtil;
import org.openmrs.module.webservices.rest.web.RequestContext;
import org.openmrs.module.webservices.rest.web.RestConstants;
import org.openmrs.module.webservices.rest.web.RestUtil;
import org.openmrs.module.webservices.rest.web.api.RestService;
import org.openmrs.module.webservices.rest.web.representation.Representation;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingCrudResource;
import org.openmrs.module.webservices.rest.web.resource.impl.DelegatingResourceDescription;
import org.openmrs.module.webservices.rest.web.response.ResourceDoesNotSupportOperationException;
import org.openmrs.module.webservices.rest.web.response.ResponseException;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs1_8.PatientResource1_8;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs1_8.PersonResource1_8;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs1_8.RelationShipTypeResource1_8;
import org.openmrs.module.webservices.rest.web.v1_0.resource.openmrs1_8.RelationshipResource1_8;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

/**
 * Controller for REST web service access to
 * the Search resource.
 */

@Controller
@RequestMapping(value = "/rest/" + RestConstants.VERSION_1 + "/bahmnicore/patientprofile")
public class BahmniPatientProfileResource extends DelegatingCrudResource<PatientProfile> {

    private EmrPatientProfileService emrPatientProfileService;
    private IdentifierSourceServiceWrapper identifierSourceServiceWrapper;

    @Autowired
    public BahmniPatientProfileResource(EmrPatientProfileService emrPatientProfileService, IdentifierSourceServiceWrapper identifierSourceServiceWrapper) {
        this.emrPatientProfileService = emrPatientProfileService;
        this.identifierSourceServiceWrapper = identifierSourceServiceWrapper;
    }

    @RequestMapping(method = RequestMethod.POST)
    @ResponseBody
    public ResponseEntity<Object> create(@RequestHeader(value = "Jump-Accepted", required = false) boolean jumpAccepted, @RequestBody SimpleObject propertiesToCreate) throws Exception {
        LinkedHashMap identifierProperties = (LinkedHashMap) ((ArrayList) ((LinkedHashMap) propertiesToCreate.get("patient")).get("identifiers")).get(0);
        String identifierPrefix = String.valueOf(identifierProperties.get("identifierPrefix"));
        identifierProperties.remove("identifierPrefix");
        String identifierSourceUuid = String.valueOf(identifierProperties.get("identifierSourceUuid"));
        String identifier;
        identifierProperties.remove("identifierSourceUuid");
        boolean isRegistrationIDNumeric = String.valueOf(identifierProperties.get("identifier")).replace(identifierPrefix, "").matches("[0-9]+");
        if (identifierProperties.get("identifier") != null && !Objects.equals(identifierPrefix, "") && isRegistrationIDNumeric) {
            long givenRegistrationNumber = Long.parseLong(String.valueOf(identifierProperties.get("identifier")).replace(identifierPrefix, ""));
            long latestRegistrationNumber = Long.parseLong(identifierSourceServiceWrapper.getSequenceValueUsingIdentifierSourceUuid(identifierSourceUuid));
            if (!jumpAccepted) {
                long sizeOfJump = givenRegistrationNumber - latestRegistrationNumber;
                if (sizeOfJump > 0) {
                    return new ResponseEntity<Object>("{\"sizeOfJump\":" + sizeOfJump + "}", HttpStatus.PRECONDITION_FAILED);
                }
            }
            if (latestRegistrationNumber < (givenRegistrationNumber + 1 ))
            identifierSourceServiceWrapper.saveSequenceValueUsingIdentifierSourceUuid(givenRegistrationNumber + 1, identifierSourceUuid);
        } else if(identifierProperties.get("identifier") == null) {
            identifier = identifierSourceServiceWrapper.generateIdentifierUsingIdentifierSourceUuid(identifierSourceUuid, "");
            identifierProperties.put("identifier", identifier);
        }

        PatientProfile delegate = mapForCreatePatient(propertiesToCreate);

        String primaryIdentifierTypeUuid = Context.getAdministrationService().getGlobalProperty("emr.primaryIdentifierType");
        PatientIdentifierType primaryIdentifierType = Context.getPatientService().getPatientIdentifierTypeByUuid(primaryIdentifierTypeUuid);

        for (PatientIdentifier patientIdentifier : delegate.getPatient().getIdentifiers()) {
            patientIdentifier.setIdentifierType(primaryIdentifierType);
        }

        setConvertedProperties(delegate, propertiesToCreate, getCreatableProperties(), true);
        try {
            delegate = emrPatientProfileService.save(delegate);
            return new ResponseEntity<>(ConversionUtil.convertToRepresentation(delegate, Representation.FULL), HttpStatus.OK);
        } catch (Exception e) {
            if (e instanceof ContextAuthenticationException) {
                return new ResponseEntity<Object>(e, HttpStatus.FORBIDDEN);
            } else if (e instanceof NonUniqueObjectException) {
                return new ResponseEntity<Object>(e, HttpStatus.OK);
            }  else if (e instanceof ValidationException) {
                return new ResponseEntity<Object>(RestUtil.wrapErrorResponse(e, null), HttpStatus.BAD_REQUEST);
            } else {
                return new ResponseEntity<Object>(e, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    @RequestMapping(method = RequestMethod.POST, value = "/{uuid}")
    @ResponseBody
    public ResponseEntity<Object> update(@PathVariable("uuid") String uuid, @RequestBody SimpleObject propertiesToCreate) throws Exception {
        PatientProfile delegate = mapForUpdatePatient(uuid, propertiesToCreate);
        setConvertedProperties(delegate, propertiesToCreate, getUpdatableProperties(), true);
        delegate.setRelationships(getRelationships(propertiesToCreate, delegate.getPatient()));
        try {
            delegate = emrPatientProfileService.save(delegate);
            return new ResponseEntity<>(ConversionUtil.convertToRepresentation(delegate, Representation.FULL), HttpStatus.OK);
        } catch (Exception e) {
            if (e instanceof ContextAuthenticationException) {
                return new ResponseEntity<Object>(e, HttpStatus.FORBIDDEN);
            } else if (e instanceof ValidationException) {
                return new ResponseEntity<Object>(RestUtil.wrapErrorResponse(e, null), HttpStatus.BAD_REQUEST);
            } else {
                return new ResponseEntity<Object>(e, HttpStatus.INTERNAL_SERVER_ERROR);
            }
        }
    }

    private PatientProfile mapForCreatePatient(SimpleObject propertiesToCreate) {
        final Object patientProperty = propertiesToCreate.get("patient");
        if (propertiesToCreate.get("patient") == null || !(propertiesToCreate.get("patient") instanceof Map)) {
            throw new ConversionException("The patient property is missing");
        }

        PatientProfile delegate = new PatientProfile();
        PatientResource1_8 patientResource1_9 = (PatientResource1_8) Context.getService(RestService.class).getResourceBySupportedClass(Patient.class);
        delegate.setPatient(patientResource1_9.getPatient(new SimpleObject() {{
            putAll((Map<String, Object>) patientProperty);
        }}));
        propertiesToCreate.removeProperty("patient");
        delegate.setRelationships(getRelationships(propertiesToCreate, delegate.getPatient()));
        propertiesToCreate.removeProperty("relationships");
        return delegate;
    }

    private PatientProfile mapForUpdatePatient(String uuid, SimpleObject propertiesToUpdate) {
        if (propertiesToUpdate.get("patient") == null || !(propertiesToUpdate.get("patient") instanceof Map)) {
            throw new ConversionException("The patient property is missing");
        }

        PatientProfile delegate = new PatientProfile();

        PatientResource1_8 patientResource1_9 = (PatientResource1_8) Context.getService(RestService.class).getResourceBySupportedClass(Patient.class);
        Patient patient = patientResource1_9.getPatientForUpdate(uuid, (Map<String, Object>) propertiesToUpdate.get("patient"));
        delegate.setPatient(patient);

        propertiesToUpdate.removeProperty("patient");
        return delegate;
    }

    private List<Relationship> getRelationships(SimpleObject propertiesToCreate, Person currentPerson) {
        Object relationshipsList = propertiesToCreate.get("relationships");
        List<Relationship> relationships = new ArrayList<Relationship>();
        List<Map<String, Object>> relationshipProperties = (List<Map<String, Object>>) relationshipsList;
        for (final Map<String, Object> relationshipProperty : relationshipProperties) {
            String uuid = getValueFromMap(relationshipProperty, "uuid");
            Relationship relationship;
            if (StringUtils.isBlank(uuid)) {
                relationship = createRelationship(currentPerson, relationshipProperty);
            } else {
                relationship = updateRelationship(relationshipProperty);
            }
            relationships.add(relationship);
        }
        return relationships;
    }

    private String getValueFromMap(Map<String, Object> jsonMap, String key) {
        Object value = jsonMap.get(key);
        return ObjectUtils.toString(value);
    }

    private Relationship createRelationship(Person currentPerson, Map<String, Object> relationshipJson) {
        Relationship relationship = new Relationship(currentPerson,
                getPerson((Map<String, Object>) relationshipJson.get("personB")),
                getRelationshipType((Map<String, Object>) relationshipJson.get("relationshipType")));
        relationship.setEndDate(new DateMapper().convertUTCToDate(getValueFromMap(relationshipJson, "endDate")));

        return relationship;
    }

    private Person getPerson(Map<String, Object> personJson) {
        String personUuid = getValueFromMap(personJson, "uuid");

        if (StringUtils.isBlank(personUuid)) {
            throw new ConversionException("The personUuid is not present.");
        }

        return getPersonFromUuid(personUuid);
    }

    private Person getPersonFromUuid(String personUuid) {
        PersonResource1_8 personResource = (PersonResource1_8) Context.getService(RestService.class).getResourceBySupportedClass(Person.class);
        Person person = personResource.getByUniqueId(personUuid);

        if (person == null) {
            throw new ConversionException("The person does not exist.");
        }
        return person;
    }

    private RelationshipType getRelationshipType(Map<String, Object> relationshipTypeJson) {

        String relationshipTypeUuid = getValueFromMap(relationshipTypeJson, "uuid");

        if (StringUtils.isBlank(relationshipTypeUuid)) {
            throw new ConversionException("The relationshipTypeUuid is not present");
        }

        RelationShipTypeResource1_8 relationshipResource = (RelationShipTypeResource1_8) Context.getService(RestService.class).getResourceBySupportedClass(RelationshipType.class);
        RelationshipType relationshipType = relationshipResource.getByUniqueId(relationshipTypeUuid);

        if (relationshipType == null) {
            throw new ConversionException("The relationship type does not exist.");
        }

        return relationshipType;
    }

    private Relationship updateRelationship(final Map<String, Object> relationshipJson) {
        String relationshipUuid = getValueFromMap(relationshipJson, "uuid");

        if (StringUtils.isBlank(relationshipUuid)) {
            throw new ConversionException("The relationshipUuid is not present");
        }

        RelationshipResource1_8 relationshipResource = (RelationshipResource1_8) Context.getService(RestService.class).getResourceBySupportedClass(Relationship.class);
        Relationship relationship = relationshipResource.getByUniqueId(relationshipUuid);

        if (null == relationship) {
            throw new ConversionException("Invalid relationship for relationshipUuid " + relationshipUuid);
        }

        relationshipResource.setConvertedProperties(relationship, relationshipJson, relationshipResource.getUpdatableProperties(), true);

        RelationshipType updatedRelationshipType = getRelationshipType((Map<String, Object>) relationshipJson.get("relationshipType"));
        relationship.setRelationshipType(updatedRelationshipType);

        return relationship;
    }

    @Override
    public PatientProfile getByUniqueId(String s) {
        return null;
    }

    @Override
    protected void delete(PatientProfile patientProfile, String s, RequestContext requestContext) throws ResponseException {

    }

    @Override
    public PatientProfile newDelegate() {
        return null;
    }

    @Override
    public PatientProfile save(PatientProfile patientProfile) {
        return null;
    }

    @Override
    public void purge(PatientProfile patientProfile, RequestContext requestContext) throws ResponseException {

    }

    public DelegatingResourceDescription getCreatableProperties() throws ResourceDoesNotSupportOperationException {
        DelegatingResourceDescription description = new DelegatingResourceDescription();
        description.addProperty("patient", Representation.DEFAULT);
        description.addProperty("image", Representation.DEFAULT);
        description.addProperty("relationships", Representation.DEFAULT);
        return description;
    }

    @Override
    public DelegatingResourceDescription getRepresentationDescription(Representation representation) {
        return null;
    }
}
