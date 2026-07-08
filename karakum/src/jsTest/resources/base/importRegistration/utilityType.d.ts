type StringFactory = () => string

export interface FactoryHolder {
    create: StringFactory
    result: ReturnType<StringFactory>
}
