const myConst1: string

const myConst2: number

/** my favorite constant */
const myConstWithComment: number

function myFunction1(firstParam: string, secondParam: number): void

function myFunction1(firstParam: boolean, secondParam: number): void

function myFunction2(firstParam: string, secondParam: number): void

function myFunction2(firstParam: boolean, secondParam: number): void

class MyClass {
    field: boolean
}

interface MyInterface {
    field: boolean
}

type MyTypeAlias = string

enum MyEnum {
    FIRST,
    SECOND,
}

const enum StatusType {
    ACTIVE = "active",
    INACTIVE = "inactive",
}

const enum MediaTrackKind {
    'audio' = "audio",
    'video' = "video",
    'screen' = "screen",
    'audioshare' = "audioshare",
}

interface ActiveItem {
    type: StatusType.ACTIVE
    name: string
}

interface InactiveItem {
    type: StatusType.INACTIVE
    name: string
    reason: string
}
