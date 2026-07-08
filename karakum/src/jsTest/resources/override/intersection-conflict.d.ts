interface Participant {
    id: string
    markers?: string[]
    role?: string
}

export type ParticipantWithRequiredMarkers = Participant & Required<Pick<Participant, 'markers'>>
