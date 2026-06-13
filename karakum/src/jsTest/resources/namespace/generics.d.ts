declare namespace ObjectNamespace {
    function isObjectsEquals<T extends string, K>(obj1: { [key in T]?: K }, obj2: { [key in T]?: K }, deep?: boolean): boolean
}
