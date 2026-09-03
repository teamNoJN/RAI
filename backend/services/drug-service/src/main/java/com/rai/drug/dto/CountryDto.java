package com.rai.drug.dto;

import com.rai.drug.entity.Country;

/** [채팅 시작 ▾] 드롭다운 (screen-02). */
public record CountryDto(String countryId, String name) {

    public static CountryDto from(Country country) {
        return new CountryDto(country.getCountryId(), country.getName());
    }
}
