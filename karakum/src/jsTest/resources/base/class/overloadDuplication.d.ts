type UserId = string;

type ExtendedUserId = string;

type InternalUserId = number;

export declare class OverloadExample {
    cacheExternalId(id: UserId | ExtendedUserId | InternalUserId, externalId: string): void;
}
