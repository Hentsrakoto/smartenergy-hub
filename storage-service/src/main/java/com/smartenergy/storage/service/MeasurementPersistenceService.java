package com.smartenergy.storage.service;

import com.smartenergy.storage.model.EnergyMeasurementEvent;

import java.util.List;

/**
 * Contrat d'orchestration de la persistance des mesures d'énergie.
 * Tout message Redis n'est acquitté (XACK) QUE si l'appel à persist() réussit sans lever d'exception.
 */
public interface MeasurementPersistenceService {

    /**
     * Persiste une mesure reçue.
     *
     * @param event L'événement à persister
     * @throws RuntimeException en cas d'échec de persistance
     */
    void persist(EnergyMeasurementEvent event);

    /**
     * Retourne la liste des événements enregistrés (utile pour les vérifications et tests).
     */
    List<EnergyMeasurementEvent> getPersistedEvents();

    /**
     * Retourne le nombre total d'événements persistés avec succès.
     */
    long getPersistedCount();

    /**
     * Vide le tampon de persistance.
     */
    void clear();
}
