package org.bahmni.module.referencedata.web.contract.mapper;

import org.bahmni.module.referencedata.labconcepts.contract.AllSamples;
import org.bahmni.module.referencedata.labconcepts.contract.AllTestsAndPanels;
import org.bahmni.module.referencedata.labconcepts.contract.Department;
import org.bahmni.module.referencedata.labconcepts.contract.LabTest;
import org.bahmni.module.referencedata.labconcepts.contract.Sample;
import org.bahmni.module.referencedata.labconcepts.mapper.LabTestMapper;
import org.bahmni.test.builder.ConceptBuilder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.openmrs.Concept;
import org.openmrs.ConceptClass;
import org.openmrs.ConceptDatatype;
import org.openmrs.ConceptNumeric;
import org.openmrs.ConceptSet;
import org.openmrs.api.ConceptService;
import org.openmrs.api.context.Context;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.Date;
import java.util.List;
import java.util.Locale;

import static org.bahmni.module.referencedata.labconcepts.advice.ConceptOperationEventInterceptorTest.createConceptSet;
import static org.bahmni.module.referencedata.labconcepts.advice.ConceptOperationEventInterceptorTest.getConceptSets;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.when;

@PrepareForTest(Context.class)
@RunWith(PowerMockRunner.class)
public class LabTestMapperTest {
    private LabTestMapper testMapper;
    private Concept sampleConcept;
    private Date dateCreated;
    private Date dateChanged;
    private Concept laboratoryConcept;
    @Mock
    private ConceptService conceptService;
    private Concept departmentConcept;
    private Concept labDepartmentConcept;
    private Concept testConcept;
    private Concept testAndPanelsConcept;
    private List<ConceptSet> sampleConceptSets;
    private List<ConceptSet> departmentConceptSets;
    private List<ConceptSet> testConceptSets;
    private ConceptSet testDepartmentConceptSet;
    private ConceptSet testSampleConceptSet;
    private ConceptNumeric conceptNumeric;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        testMapper = new LabTestMapper();
        dateCreated = new Date();
        dateChanged = new Date();
        Locale defaultLocale = new Locale("en", "GB");
        PowerMockito.mockStatic(Context.class);
        when(Context.getLocale()).thenReturn(defaultLocale);
        testConcept = new ConceptBuilder().withUUID("Test UUID").withDateCreated(dateCreated).withClass(LabTest.LAB_TEST_CONCEPT_CLASS).withDescription("SomeDescription")
                .withDateChanged(dateChanged).withShortName("ShortName").withName("Test Name Here").withDataType(ConceptDatatype.NUMERIC).build();
        testAndPanelsConcept = new ConceptBuilder().withUUID("Test and Panels UUID").withDateCreated(dateCreated).withClassUUID(ConceptClass.CONVSET_UUID)
                .withDateChanged(dateChanged).withShortName("ShortName").withName(AllTestsAndPanels.ALL_TESTS_AND_PANELS).withSetMember(testConcept).build();
        sampleConcept = new ConceptBuilder().withUUID("Sample UUID").withDateCreated(dateCreated).withClass(Sample.SAMPLE_CONCEPT_CLASS).
                withDateChanged(dateChanged).withSetMember(testConcept).withShortName("ShortName").withName("SampleName").build();
        laboratoryConcept = new ConceptBuilder().withUUID("Laboratory UUID")
                .withName(AllSamples.ALL_SAMPLES).withClassUUID(ConceptClass.LABSET_UUID)
                .withSetMember(sampleConcept).build();
        departmentConcept = new ConceptBuilder().withUUID("Department UUID").withDateCreated(dateCreated).
                withDateChanged(dateChanged).withClass(Department.DEPARTMENT_CONCEPT_CLASS).withSetMember(testConcept).withDescription("Some Description").withName("Department Name").build();
        labDepartmentConcept = new ConceptBuilder().withUUID("Laboratory Department UUID")
                .withName(Department.DEPARTMENT_PARENT_CONCEPT_NAME).withClassUUID(ConceptClass.CONVSET_UUID)
                .withSetMember(departmentConcept).build();
        ConceptSet sampleConceptSet = createConceptSet(laboratoryConcept, sampleConcept);
        ConceptSet departmentConceptSet = createConceptSet(labDepartmentConcept, departmentConcept);
        ConceptSet testConceptSet = createConceptSet(testAndPanelsConcept, testConcept);
        testSampleConceptSet = createConceptSet(sampleConcept, testConcept);
        testDepartmentConceptSet = createConceptSet(departmentConcept, testConcept);
        departmentConceptSets = getConceptSets(departmentConceptSet);
        sampleConceptSets = getConceptSets(sampleConceptSet);
        testConceptSets = getConceptSets(testConceptSet);
        testConceptSets.add(testSampleConceptSet);
        testConceptSets.add(testDepartmentConceptSet);
        conceptNumeric = new ConceptNumeric(testConcept);
        conceptNumeric.setUnits("unit");

        when(conceptService.getSetsContainingConcept(any(Concept.class))).thenAnswer(new Answer<List<ConceptSet>>() {
            @Override
            public List<ConceptSet> answer(InvocationOnMock invocation) throws Throwable {
                Object[] arguments = invocation.getArguments();
                Concept concept = (Concept) arguments[0];
                if (concept.getUuid().equals("Test UUID"))
                    return testConceptSets;
                else if (concept.getUuid().equals("Sample UUID"))
                    return sampleConceptSets;
                else if (concept.getUuid().equals("Department UUID"))
                    return departmentConceptSets;

                return null;
            }
        });
        when(conceptService.getConceptNumeric(anyInt())).thenReturn(conceptNumeric);
        when(Context.getConceptService()).thenReturn(conceptService);
    }

    @Test
    public void map_all_test_fields_from_concept() throws Exception {
        LabTest testData = testMapper.map(testConcept);
        assertEquals("Test UUID", testData.getId());
        assertEquals("Test Name Here", testData.getName());
        assertEquals(ConceptDatatype.NUMERIC, testData.getResultType());
        assertEquals(dateCreated, testData.getDateCreated());
        assertEquals(dateChanged, testData.getLastUpdated());
        assertEquals("unit", testData.getTestUnitOfMeasure());
    }

    @Test
    public void testUnitOfMeasure_is_null_if_not_specified() throws Exception {
        when(conceptService.getConceptNumeric(anyInt())).thenReturn(null);
        LabTest testData = testMapper.map(testConcept);
        assertNull(testData.getTestUnitOfMeasure());
    }

    @Test
    public void should_set_name_if_description_is_null() throws Exception {
        Concept testConceptWithOutDescription = new ConceptBuilder().withUUID("Test UUID").withDateCreated(dateCreated).withClassUUID(ConceptClass.TEST_UUID)
                .withDateChanged(dateChanged).withShortName("ShortName").withName("Test Name Here").withDataType(ConceptDatatype.NUMERIC).build();

        LabTest testData = testMapper.map(testConceptWithOutDescription);
        assertEquals("Test Name Here", testData.getDescription());

    }

}