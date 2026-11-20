package net.yozmnews.digest.application;

import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.domain.content.ItemRepository;
import net.yozmnews.digest.domain.digest.DigestId;
import net.yozmnews.event.EventId;
import net.yozmnews.feedback.DigestItems;
import net.yozmnews.platform.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@code feedback} 이 선언한 항목 확인 {@link DigestItems} 의 구현 — docs/specs/digest.md §4 · digest-R14. 역할 애너테이션이
 * 없다(두 역할 공통).
 */
@Service
class DigestItemsService implements DigestItems {

	private final ItemRepository items;

	DigestItemsService(ItemRepository items) {
		this.items = Objects.requireNonNull(items, "items");
	}

	@Override
	@Transactional(readOnly = true)
	public List<EventId> itemOf(UserId user, long digestId, EventId event) {
		Objects.requireNonNull(user, "user");
		Objects.requireNonNull(event, "event");
		if (digestId < 1) {
			return List.of();
		}
		return this.items.itemOf(user, new DigestId(digestId), event);
	}

}
