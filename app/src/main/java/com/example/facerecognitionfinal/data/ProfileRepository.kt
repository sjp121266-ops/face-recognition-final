package com.example.facerecognitionfinal.data

class ProfileRepository(
    initialProfiles: List<PersonProfile>,
    private val saveProfiles: (List<PersonProfile>) -> Unit,
    private val clearProfiles: () -> Unit
) {

    val profiles: MutableList<PersonProfile> = initialProfiles.toMutableList()

    fun replace(newProfiles: List<PersonProfile>) {
        profiles.clear()
        profiles.addAll(newProfiles)
        saveProfiles(profiles)
    }

    fun update(mutator: (MutableList<PersonProfile>) -> Unit) {
        mutator(profiles)
        saveProfiles(profiles)
    }

    fun deletePerson(name: String): Boolean {
        val removed = profiles.removeAll { it.name == name }
        if (removed) {
            saveProfiles(profiles)
        }
        return removed
    }

    fun clear() {
        profiles.clear()
        clearProfiles()
    }
}
