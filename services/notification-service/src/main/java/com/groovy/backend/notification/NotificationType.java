package com.groovy.backend.notification;

public enum NotificationType {
	// 방장에게: 누군가 내 스터디에 참여 신청함
	APPLICATION_RECEIVED,
	// 신청자에게: 내 신청이 승인됨
	APPLICATION_APPROVED,
	// 신청자에게: 내 신청이 거절됨
	APPLICATION_REJECTED,
	// 스터디 멤버 전원에게: 그 스터디의 일정이 생성/수정/삭제됨
	STUDY_SCHEDULE_CHANGED,
	// 대기열 등록자 전원에게: 정원이 가득 찼던 스터디에 빈자리가 생김
	WAITLIST_SEAT_OPENED,
	// 회고록 작성자에게: 내 회고록에 댓글이 달림(작성자 본인이 단 댓글은 제외)
	MEMOIR_COMMENT_ADDED,
	// 회고록 작성자에게: 내 회고록에 좋아요가 눌림(작성자 본인이 누른 좋아요는 제외)
	MEMOIR_LIKE_ADDED,
	// 스터디 멤버 전원(방장 포함)에게: 회고록/댓글로 쌓인 경험치가 다음 레벨에 도달함
	STUDY_LEVEL_UP
}
