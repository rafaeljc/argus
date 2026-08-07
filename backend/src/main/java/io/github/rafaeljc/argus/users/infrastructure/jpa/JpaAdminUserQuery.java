package io.github.rafaeljc.argus.users.infrastructure.jpa;

import io.github.rafaeljc.argus.common.application.PageResult;
import io.github.rafaeljc.argus.common.domain.UserId;
import io.github.rafaeljc.argus.users.application.AdminUserSearchCriteria;
import io.github.rafaeljc.argus.users.application.port.AdminUserQuery;
import io.github.rafaeljc.argus.users.domain.User;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

@Repository
class JpaAdminUserQuery implements AdminUserQuery {

    private final SpringDataUserJpaRepository jpa;

    JpaAdminUserQuery(SpringDataUserJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public PageResult<User> search(AdminUserSearchCriteria criteria, int page, int perPage) {
        Specification<UserJpaEntity> spec = Specification.allOf(specifications(criteria));
        Sort sort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));
        Page<UserJpaEntity> result = jpa.findAll(spec, PageRequest.of(page - 1, perPage, sort));
        List<User> items = result.getContent().stream().map(UserEntityMapper::toDomain).toList();
        return new PageResult<>(items, (int) result.getTotalElements(), page, perPage);
    }

    @Override
    public Optional<User> findById(UserId id) {
        return jpa.findById(id.value()).map(UserEntityMapper::toDomain);
    }

    private static List<Specification<UserJpaEntity>> specifications(AdminUserSearchCriteria criteria) {
        List<Specification<UserJpaEntity>> specs = new ArrayList<>();
        if (criteria.emailContains() != null) {
            specs.add(AdminUserSpecifications.emailContains(criteria.emailContains()));
        }
        if (criteria.isSuspended() != null) {
            specs.add(AdminUserSpecifications.isSuspended(criteria.isSuspended()));
        }
        if (criteria.isDeleted() != null) {
            specs.add(AdminUserSpecifications.isDeleted(criteria.isDeleted()));
        }
        if (criteria.isVerified() != null) {
            specs.add(AdminUserSpecifications.isVerified(criteria.isVerified()));
        }
        return specs;
    }
}
