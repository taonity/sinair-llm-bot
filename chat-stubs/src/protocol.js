// Packet types matching the real sinair.net chat protocol
export const PacketType = {
    error: 0,
    system: 1,
    message: 2,
    online_list: 3,
    auth: 4,
    status: 5,
    join: 6,
    leave: 7,
    create_room: 8,
    remove_room: 9,
    ping: 10,
};

export const UserStatus = {
    bad: 0,
    offline: 1,
    online: 2,
    away: 3,
    nick_change: 4,
    gender_change: 5,
    color_change: 6,
    back: 7,
    typing: 8,
    stop_typing: 9,
    orphan: 10,
};

export const MessageStyle = {
    message: 0,
    me: 1,
    event: 2,
    offtop: 3,
};
