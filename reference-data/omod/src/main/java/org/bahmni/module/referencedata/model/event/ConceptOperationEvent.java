package org.bahmni.module.referencedata.model.event;

import org.ict4h.atomfeed.server.service.Event;
import org.joda.time.DateTime;
import org.openmrs.Concept;
import org.openmrs.ConceptSet;
import org.openmrs.api.context.Context;

import java.net.URISyntaxException;
import java.util.List;
import java.util.UUID;

import static java.util.Arrays.asList;

public abstract class ConceptOperationEvent {
    String url;
    String title;
    String category;

    public ConceptOperationEvent(String url, String category, String title) {
        this.url = url;
        this.title = title;
        this.category = category;
    }

    public ConceptOperationEvent() {
    }

    public abstract Boolean isApplicable(String operation, Object[] arguments);

    List<String> operations() {
        return asList("saveConcept", "updateConcept", "retireConcept", "purgeConcept");
    }

    public Event asAtomFeedEvent(Object[] arguments) throws URISyntaxException {
        Concept concept = (Concept) arguments[0];
        String url = String.format(this.url, title, concept.getUuid());
        return new Event(UUID.randomUUID().toString(), title, DateTime.now(), url, url, category);
    }

    public boolean isChildOf(Concept concept, String parentConceptName) {
        List<ConceptSet> conceptSets = Context.getConceptService().getSetsContainingConcept(concept);
        for (ConceptSet conceptSet : conceptSets) {
            if (conceptSet.getConceptSet().getName(Context.getLocale()).getName().equals(parentConceptName)) {
                return true;
            }
        }
        return false;
    }
}