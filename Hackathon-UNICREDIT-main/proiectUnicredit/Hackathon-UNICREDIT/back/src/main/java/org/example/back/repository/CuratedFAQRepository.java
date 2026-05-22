package org.example.back.repository;

import org.example.back.model.CuratedFAQ;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CuratedFAQRepository extends JpaRepository<CuratedFAQ, Long> {
}