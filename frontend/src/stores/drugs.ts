import { defineStore } from 'pinia'
import { ref } from 'vue'
import { api } from '@/api/client'
import type { Drug, DrugPatchResponse, ReassessmentNeeded } from '@/types/api'

export const useDrugStore = defineStore('drugs', () => {
  const drugs = ref<Drug[]>([])
  const loading = ref(false)

  async function load(q = '') {
    loading.value = true
    try {
      drugs.value = await api<Drug[]>(
        'GET',
        q ? `/api/drugs?q=${encodeURIComponent(q)}` : '/api/drugs',
      )
    } finally {
      loading.value = false
    }
  }

  async function register(payload: {
    product_name: string
    ingredients: string[]
    strength: string
    dosage_form: string
  }) {
    const res = await api<{ drug_id: string }>('POST', '/api/drugs', payload)
    await load()
    return res.drug_id
  }

  /** 성분/버전 변경 — PATCH 성공 시 version+1, 판정 이력 있으면 재검토 배너 트리거 */
  async function update(
    drugId: string,
    payload: { ingredients?: string[]; strength?: string; dosage_form?: string },
  ): Promise<DrugPatchResponse> {
    const res = await api<DrugPatchResponse>('PATCH', `/api/drugs/${drugId}`, payload)
    await load()
    return res
  }

  function reassessmentNeeded(drugId: string): Promise<ReassessmentNeeded> {
    return api<ReassessmentNeeded>('GET', `/api/drugs/${drugId}/reassessment-needed`)
  }

  return { drugs, loading, load, register, update, reassessmentNeeded }
})
