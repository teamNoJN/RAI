package com.rai.drug.service;

import com.rai.drug.dto.CountryDto;
import com.rai.drug.repository.CountryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CountryService {

    private final CountryRepository countryRepository;

    @Transactional(readOnly = true)
    public List<CountryDto> list() {
        return countryRepository.findAllByOrderByNameAsc().stream().map(CountryDto::from).toList();
    }
}
