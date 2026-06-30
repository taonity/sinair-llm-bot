import { PacketType, UserStatus, MessageStyle } from './protocol.js';

const FAKE_USERS = [
    { member_id: 1, user_id: 101, name: 'Alice', color: '#e74c3c', girl: true, is_moder: false, is_owner: true },
    { member_id: 2, user_id: 102, name: 'Bob', color: '#3498db', girl: false, is_moder: true, is_owner: false },
    { member_id: 3, user_id: 103, name: 'Charlie', color: '#2ecc71', girl: false, is_moder: false, is_owner: false },
    { member_id: 4, user_id: 104, name: 'Diana', color: '#9b59b6', girl: true, is_moder: false, is_owner: false },
    { member_id: 5, user_id: 105, name: 'Eve', color: '#f39c12', girl: false, is_moder: false, is_owner: false },
];

const SAMPLE_MESSAGES = [
    'Привет всем!',
    'Как дела?',
    'Кто-нибудь видел новый фильм?',
    'Сегодня отличная погода',
    'Кто за партейку?',
    'лол',
    'Ну такое',
    'Согласен',
    'Не, я пас',
    'О, привет!',
    'Расскажите что нового',
    'Вчера был на концерте, было круто',
    'Кто хочет пиццу?',
    'Я уже ложусь спать, всем спокойной ночи',
    'Да ладно, серьезно?',
    'Хахаха',
    ':D',
    'Ничего себе',
    'А я думал это только у меня так',
    'Ну вот и славненько',
];

let messageIdCounter = 1000;

export function generateMessageId() {
    return String(messageIdCounter++);
}

export function generateHistoryMessages(roomTarget, count = 50) {
    const messages = [];
    const now = Math.floor(Date.now() / 1000);

    for (let i = count; i > 0; i--) {
        const user = FAKE_USERS[Math.floor(Math.random() * FAKE_USERS.length)];
        const text = SAMPLE_MESSAGES[Math.floor(Math.random() * SAMPLE_MESSAGES.length)];
        const time = now - i * 30;

        messages.push({
            type: PacketType.message,
            id: generateMessageId(),
            color: user.color,
            from: user.member_id,
            from_login: user.name,
            message: text,
            style: MessageStyle.message,
            target: roomTarget,
            time,
            to: 0,
        });
    }

    return messages;
}

export function generateRandomMessage(roomTarget) {
    const user = FAKE_USERS[Math.floor(Math.random() * FAKE_USERS.length)];
    const text = SAMPLE_MESSAGES[Math.floor(Math.random() * SAMPLE_MESSAGES.length)];

    return {
        type: PacketType.message,
        id: generateMessageId(),
        color: user.color,
        from: user.member_id,
        from_login: user.name,
        message: text,
        style: MessageStyle.message,
        target: roomTarget,
        time: Math.floor(Date.now() / 1000),
        to: 0,
    };
}

export function generateOnlineList(roomTarget) {
    return FAKE_USERS.map(u => ({
        type: PacketType.status,
        target: roomTarget,
        ...u,
        status: UserStatus.online,
        last_seen_time: Math.floor(Date.now() / 1000),
        typing: false,
    }));
}

export function generateStatusEvent(roomTarget) {
    const events = [
        () => {
            const user = FAKE_USERS[Math.floor(Math.random() * FAKE_USERS.length)];
            return {
                type: PacketType.status,
                target: roomTarget,
                ...user,
                status: UserStatus.away,
                last_seen_time: Math.floor(Date.now() / 1000),
            };
        },
        () => {
            const user = FAKE_USERS[Math.floor(Math.random() * FAKE_USERS.length)];
            return {
                type: PacketType.status,
                target: roomTarget,
                ...user,
                status: UserStatus.back,
                last_seen_time: Math.floor(Date.now() / 1000),
            };
        },
        () => {
            const user = FAKE_USERS[Math.floor(Math.random() * FAKE_USERS.length)];
            const oldName = user.name;
            const newName = user.name + '_' + Math.floor(Math.random() * 100);
            return {
                type: PacketType.status,
                target: roomTarget,
                ...user,
                name: newName,
                data: oldName,
                status: UserStatus.nick_change,
                last_seen_time: Math.floor(Date.now() / 1000),
            };
        },
    ];

    const gen = events[Math.floor(Math.random() * events.length)];
    return gen();
}

export { FAKE_USERS };
